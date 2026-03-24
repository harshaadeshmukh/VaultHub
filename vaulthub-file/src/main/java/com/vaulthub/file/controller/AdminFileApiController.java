package com.vaulthub.file.controller;

import com.vaulthub.file.entity.FileRecord;
import com.vaulthub.file.repository.FileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@Slf4j
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminFileApiController {

    private final FileRepository fileRepository;

    // Per-user stats keyed by ownerId
    @GetMapping("/user-stats")
    public ResponseEntity<Map<String, Map<String, Object>>> getUserStats() {
        Map<String, Map<String, Object>> result = new HashMap<>();
        fileRepository.findAll().forEach(f -> {
            String key = String.valueOf(f.getOwnerId());
            result.computeIfAbsent(key, k -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("fileCount", 0);
                m.put("totalBytes", 0L);
                return m;
            });
            Map<String, Object> m = result.get(key);
            m.put("fileCount",  ((int)  m.get("fileCount"))  + 1);
            m.put("totalBytes", ((long) m.get("totalBytes")) + (f.getFileSize() != null ? f.getFileSize() : 0L));
        });
        return ResponseEntity.ok(result);
    }

    // All files for a specific ownerId (for user detail page)
    @GetMapping("/user-files-list/{ownerId}")
    public ResponseEntity<List<Map<String, Object>>> getUserFiles(@PathVariable Long ownerId) {
        List<Map<String, Object>> files = fileRepository
                .findByOwnerIdOrderByCreatedAtDesc(ownerId)
                .stream()
                .map(f -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("fileUuid",  f.getFileUuid());
                    m.put("fileName",  f.getFileName());
                    m.put("fileSize",  f.getFileSize());
                    m.put("mimeType",  f.getMimeType());
                    m.put("status",    f.getStatus().name());
                    m.put("createdAt", f.getCreatedAt() != null ? f.getCreatedAt().toString() : "—");
                    m.put("sizeMB",    String.format("%.2f MB", f.getFileSize() / 1048576.0));
                    return m;
                })
                .toList();
        return ResponseEntity.ok(files);
    }

    // ALL files across ALL users (for admin files view)
    @GetMapping("/all-files")
    public ResponseEntity<List<Map<String, Object>>> getAllFiles() {
        List<Map<String, Object>> files = fileRepository
                .findAll()
                .stream()
                .sorted(Comparator.comparing(FileRecord::getCreatedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .map(f -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("fileUuid",  f.getFileUuid());
                    m.put("fileName",  f.getFileName());
                    m.put("fileSize",  f.getFileSize());
                    m.put("mimeType",  f.getMimeType());
                    m.put("status",    f.getStatus().name());
                    m.put("ownerId",   f.getOwnerId());
                    m.put("createdAt", f.getCreatedAt() != null ? f.getCreatedAt().toString() : "—");
                    m.put("sizeMB",    String.format("%.2f MB", f.getFileSize() / 1048576.0));
                    return m;
                })
                .toList();
        return ResponseEntity.ok(files);
    }

    // Delete all files owned by ownerId
    @DeleteMapping("/user-files/{ownerId}")
    public ResponseEntity<Map<String, Object>> deleteUserFiles(@PathVariable Long ownerId) {
        try {
            var files = fileRepository.findByOwnerIdOrderByCreatedAtDesc(ownerId);
            fileRepository.deleteAll(files);
            log.info("🗑️ Deleted {} files for ownerId {}", files.size(), ownerId);
            return ResponseEntity.ok(Map.of("deleted", files.size()));
        } catch (Exception e) {
            log.error("Failed to delete files for ownerId {}", ownerId, e);
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }
}
