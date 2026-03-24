package com.vaulthub.file.service;

import com.vaulthub.file.dto.FileResponse;
import com.vaulthub.file.entity.ChunkRecord;
import com.vaulthub.file.entity.FileRecord;
import com.vaulthub.file.repository.ChunkRepository;
import com.vaulthub.file.repository.FileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class FileService {

    private final FileRepository fileRepository;
    private final ChunkRepository chunkRepository;

    @Value("${storage.chunk-size-bytes:10485760}")
    private int chunkSizeBytes;

    @Value("${storage.path:./vault-storage}")
    private String storagePath;

    // ══════════════════════════════════════════
    //   UPLOAD — stream directly to disk, no RAM buffering
    // ══════════════════════════════════════════
    @Transactional
    public FileResponse uploadFile(MultipartFile file, Long ownerId) throws IOException {

        String fileUuid = UUID.randomUUID().toString();
        Path storageDir = Paths.get(storagePath).toAbsolutePath();
        if (!Files.exists(storageDir)) Files.createDirectories(storageDir);

        long totalSize = file.getSize();
        int totalChunks = (int) Math.ceil((double) totalSize / chunkSizeBytes);

        // Save FileRecord first
        FileRecord fileRecord = FileRecord.builder()
                .fileName(file.getOriginalFilename())
                .fileSize(totalSize)
                .mimeType(file.getContentType() != null ? file.getContentType() : "application/octet-stream")
                .totalChunks(totalChunks)
                .ownerId(ownerId)
                .fileUuid(fileUuid)
                .status(FileRecord.FileStatus.UPLOADING)
                .build();
        fileRecord = fileRepository.save(fileRecord);

        // Stream directly from multipart to disk — no full file in RAM
        try (InputStream in = new BufferedInputStream(file.getInputStream(), 65536)) {
            byte[] buffer = new byte[chunkSizeBytes];
            int chunkIndex = 0;
            int bytesRead;

            while ((bytesRead = readFully(in, buffer)) > 0) {
                byte[] chunkBytes = bytesRead < buffer.length
                        ? java.util.Arrays.copyOf(buffer, bytesRead)
                        : buffer.clone();

                String chunkFileName = fileUuid + "_" + chunkIndex + ".chunk";
                Path chunkPath = storageDir.resolve(chunkFileName);
                Files.write(chunkPath, chunkBytes, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

                // Save chunk record
                ChunkRecord cr = ChunkRecord.builder()
                        .fileId(fileRecord.getId())
                        .chunkIndex(chunkIndex)
                        .chunkSize((long) chunkBytes.length)
                        .storagePath(chunkPath.toString())
                        .build();
                chunkRepository.save(cr);
                chunkIndex++;
            }
        }

        fileRecord.setStatus(FileRecord.FileStatus.READY);
        fileRepository.save(fileRecord);
        log.info("✅ Upload complete: {} ({} chunks)", file.getOriginalFilename(), totalChunks);
        return toResponse(fileRecord);
    }

    // Read up to buffer.length bytes filling the buffer fully before returning
    private int readFully(InputStream in, byte[] buf) throws IOException {
        int total = 0;
        while (total < buf.length) {
            int n = in.read(buf, total, buf.length - total);
            if (n < 0) break;
            total += n;
        }
        return total;
    }

    // ══════════════════════════════════════════
    //   STREAM — returns Path so controller can stream with zero RAM copy
    // ══════════════════════════════════════════
    public FileRecord getFileRecord(String fileUuid) {
        return fileRepository.findByFileUuid(fileUuid)
                .orElseThrow(() -> new RuntimeException("File not found: " + fileUuid));
    }

    public List<ChunkRecord> getChunks(Long fileId) {
        return chunkRepository.findByFileIdOrderByChunkIndex(fileId);
    }

    // ══════════════════════════════════════════
    //   DOWNLOAD — reassemble into byte[] (kept for small files / compat)
    // ══════════════════════════════════════════
    public byte[] downloadFile(String fileUuid) throws IOException {
        FileRecord rec = getFileRecord(fileUuid);
        List<ChunkRecord> chunks = getChunks(rec.getId());

        ByteArrayOutputStream out = new ByteArrayOutputStream(rec.getFileSize().intValue());
        for (ChunkRecord chunk : chunks) {
            out.write(Files.readAllBytes(Paths.get(chunk.getStoragePath())));
        }
        return out.toByteArray();
    }

    // ══════════════════════════════════════════
    //   GET FILES FOR USER
    // ══════════════════════════════════════════
    public List<FileResponse> getFilesForUser(Long ownerId) {
        return fileRepository.findByOwnerIdOrderByCreatedAtDesc(ownerId)
                .stream().map(this::toResponse).toList();
    }

    public FileResponse getFile(String fileUuid) {
        return toResponse(getFileRecord(fileUuid));
    }

    // ══════════════════════════════════════════
    //   DELETE
    // ══════════════════════════════════════════
    @Transactional
    public void deleteFile(String fileUuid, Long ownerId) {
        FileRecord record = fileRepository.findByFileUuid(fileUuid)
                .orElseThrow(() -> new RuntimeException("File not found"));

        if (record.getOwnerId().longValue() != ownerId)
            throw new RuntimeException("You don't have permission to delete this file!");

        List<ChunkRecord> chunks = chunkRepository.findByFileIdOrderByChunkIndex(record.getId());
        for (ChunkRecord chunk : chunks) {
            try { Files.deleteIfExists(Paths.get(chunk.getStoragePath())); }
            catch (IOException e) { log.warn("Could not delete chunk: {}", chunk.getStoragePath()); }
        }
        chunkRepository.deleteByFileId(record.getId());
        fileRepository.delete(record);
        log.info("✅ File deleted: {}", fileUuid);
    }

    // ══════════════════════════════════════════
    //   STATS
    // ══════════════════════════════════════════
    public long getTotalStorageBytes(Long ownerId) {
        return fileRepository.findByOwnerIdOrderByCreatedAtDesc(ownerId)
                .stream().mapToLong(FileRecord::getFileSize).sum();
    }

    // ══════════════════════════════════════════
    //   RENAME
    // ══════════════════════════════════════════
    public FileResponse renameFile(String fileUuid, Long ownerId, String newName) {
        FileRecord record = fileRepository.findByFileUuid(fileUuid)
                .orElseThrow(() -> new RuntimeException("File not found: " + fileUuid));
        if (record.getOwnerId().longValue() != ownerId) throw new RuntimeException("Access denied");
        String trimmed = newName.trim();
        if (trimmed.isBlank()) throw new RuntimeException("Name cannot be blank");
        record.setFileName(trimmed);
        fileRepository.save(record);
        return toResponse(record);
    }

    private FileResponse toResponse(FileRecord record) {
        return FileResponse.builder()
                .id(record.getId())
                .fileName(record.getFileName())
                .fileSize(record.getFileSize())
                .mimeType(record.getMimeType())
                .totalChunks(record.getTotalChunks())
                .fileUuid(record.getFileUuid())
                .status(record.getStatus().name())
                .createdAt(record.getCreatedAt())
                .build();
    }
}
