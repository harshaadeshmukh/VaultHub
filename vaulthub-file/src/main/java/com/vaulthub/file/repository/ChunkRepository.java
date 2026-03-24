package com.vaulthub.file.repository;

import com.vaulthub.file.entity.ChunkRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChunkRepository
        extends JpaRepository<ChunkRecord, Long> {

    // 🧠 Get all chunks for a file IN ORDER
    //    chunkIndex 0, 1, 2, 3... must be in order
    //    to reassemble the file correctly!
    List<ChunkRecord> findByFileIdOrderByChunkIndex(Long fileId);

    // 🧠 Delete all chunks for a file
    void deleteByFileId(Long fileId);

    // 🧠 Count chunks for a file
    long countByFileId(Long fileId);
}