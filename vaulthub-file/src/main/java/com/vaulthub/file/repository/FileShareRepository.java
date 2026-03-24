package com.vaulthub.file.repository;

import com.vaulthub.file.entity.FileShare;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface FileShareRepository extends JpaRepository<FileShare, Long> {

    Optional<FileShare> findByFileUuidAndSharedWithEmail(String fileUuid, String sharedWithEmail);

    List<FileShare> findByFileUuidAndOwnerIdOrderBySharedAtDesc(String fileUuid, Long ownerId);

    List<FileShare> findBySharedWithOwnerIdAndActiveTrue(Long sharedWithOwnerId);

    List<FileShare> findByOwnerIdOrderBySharedAtDesc(Long ownerId);

    List<FileShare> findByFileUuidAndActiveTrue(String fileUuid);

    // Find all active shares that have passed their expiry time
    @Query("SELECT s FROM FileShare s WHERE s.active = true AND s.expiresAt IS NOT NULL AND s.expiresAt < :now")
    List<FileShare> findExpiredActiveShares(@Param("now") LocalDateTime now);
}
