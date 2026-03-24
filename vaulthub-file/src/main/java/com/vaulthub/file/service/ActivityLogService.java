package com.vaulthub.file.service;

import com.vaulthub.file.entity.ActivityLog;
import com.vaulthub.file.entity.ActivityLog.ActivityType;
import com.vaulthub.file.repository.ActivityLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ActivityLogService {

    private final ActivityLogRepository repo;

    // ── Log any action ──────────────────────────────────────────
    public void log(Long ownerId, ActivityType type,
                    String fileName, String fileUuid, String detail) {
        try {
            repo.save(ActivityLog.builder()
                    .ownerId(ownerId)
                    .type(type)
                    .fileName(fileName)
                    .fileUuid(fileUuid)
                    .detail(detail)
                    .build());
        } catch (Exception e) {
            // Never let logging crash the main flow
            log.warn("Activity log failed: {}", e.getMessage());
        }
    }

    // Convenience overloads ─────────────────────────────────────
    public void logUpload(Long ownerId, String fileName, String fileUuid) {
        log(ownerId, ActivityType.UPLOAD, fileName, fileUuid, null);
    }

    public void logDownload(Long ownerId, String fileName, String fileUuid) {
        log(ownerId, ActivityType.DOWNLOAD, fileName, fileUuid, null);
    }

    public void logDelete(Long ownerId, String fileName, String fileUuid) {
        log(ownerId, ActivityType.DELETE, fileName, fileUuid, null);
    }

    public void logView(Long ownerId, String fileName, String fileUuid) {
        log(ownerId, ActivityType.VIEW, fileName, fileUuid, null);
    }

    public void logShareSent(Long ownerId, String fileName, String fileUuid, String toEmail) {
        log(ownerId, ActivityType.SHARE_SENT, fileName, fileUuid, toEmail);
    }

    public void logShareReceived(Long ownerId, String fileName, String fileUuid, String fromEmail) {
        log(ownerId, ActivityType.SHARE_RECEIVED, fileName, fileUuid, fromEmail);
    }

    // ── Fetch recent logs ───────────────────────────────────────
    public List<ActivityLog> getRecent(Long ownerId, int limit) {
        return repo.findByOwnerIdOrderByCreatedAtDesc(
                ownerId, PageRequest.of(0, limit));
    }

    public long count(Long ownerId) {
        return repo.countByOwnerId(ownerId);
    }

    // ── Idempotency checks ──────────────────────────────────────
    public boolean existsForFile(Long ownerId, String fileUuid) {
        return repo.existsByOwnerIdAndFileUuid(ownerId, fileUuid);
    }

    public boolean existsShareLog(Long ownerId, String fileUuid, String recipientEmail) {
        return repo.existsByOwnerIdAndFileUuidAndDetail(ownerId, fileUuid, recipientEmail);
    }
}
