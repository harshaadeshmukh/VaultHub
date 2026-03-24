package com.vaulthub.file.repository;

import com.vaulthub.file.entity.FileRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FileRepository
        extends JpaRepository<FileRecord, Long> {

    // 🧠 Get all files belonging to a user
    List<FileRecord> findByOwnerIdOrderByCreatedAtDesc(Long ownerId);

    // 🧠 Find file by its UUID
    Optional<FileRecord> findByFileUuid(String fileUuid);

    // 🧠 Count files for a user
    long countByOwnerId(Long ownerId);
}