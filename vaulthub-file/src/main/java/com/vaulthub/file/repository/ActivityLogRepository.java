package com.vaulthub.file.repository;

import com.vaulthub.file.entity.ActivityLog;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ActivityLogRepository extends JpaRepository<ActivityLog, Long> {

    List<ActivityLog> findByOwnerIdOrderByCreatedAtDesc(Long ownerId, Pageable pageable);

    long countByOwnerId(Long ownerId);

    boolean existsByOwnerIdAndFileUuid(Long ownerId, String fileUuid);

    boolean existsByOwnerIdAndFileUuidAndDetail(Long ownerId, String fileUuid, String detail);

    // Count uploads per day for last N days (for trend chart)
    @Query("SELECT CAST(a.createdAt AS date), COUNT(a) FROM ActivityLog a " +
           "WHERE a.ownerId = :ownerId AND a.type = 'UPLOAD' AND a.createdAt >= :since " +
           "GROUP BY CAST(a.createdAt AS date) ORDER BY CAST(a.createdAt AS date)")
    List<Object[]> countUploadsByDay(@Param("ownerId") Long ownerId,
                                     @Param("since") LocalDateTime since);

    // Top viewed files
    @Query("SELECT a.fileUuid, a.fileName, COUNT(a) as views FROM ActivityLog a " +
           "WHERE a.ownerId = :ownerId AND a.type = 'VIEW' " +
           "GROUP BY a.fileUuid, a.fileName ORDER BY views DESC")
    List<Object[]> topViewedFiles(@Param("ownerId") Long ownerId, Pageable pageable);

    // View count for a specific file
    long countByOwnerIdAndFileUuidAndType(Long ownerId, String fileUuid,
                                          ActivityLog.ActivityType type);
}
