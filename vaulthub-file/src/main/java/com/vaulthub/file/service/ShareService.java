package com.vaulthub.file.service;

import com.vaulthub.file.entity.FileRecord;
import com.vaulthub.file.entity.FileShare;
import com.vaulthub.file.repository.FileRepository;
import com.vaulthub.file.repository.FileShareRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ShareService {

    private final FileShareRepository shareRepository;
    private final FileRepository fileRepository;
    private final RestTemplate restTemplate = new RestTemplate();

    private static final String AUTH_LOOKUP_URL = "http://localhost:8081/api/users/by-email?email=";

    // ══════════════════════════════════════════
    //  LOOKUP a vault user by email
    // ══════════════════════════════════════════
    public Map<String, Object> lookupVaultUser(String email) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> result = restTemplate.getForObject(
                    AUTH_LOOKUP_URL + email.trim().toLowerCase(), Map.class);
            if (result != null && Boolean.TRUE.equals(result.get("found"))) return result;
            return null;
        } catch (Exception e) {
            log.error("User lookup failed for email {}: {}", email, e.getMessage());
            return null;
        }
    }

    // ══════════════════════════════════════════
    //  SHARE a file  (expiryHours = 0 → never expires)
    // ══════════════════════════════════════════
    @Transactional
    public FileShare shareFile(String fileUuid, Long ownerId, String ownerEmail,
                               String recipientEmail, int expiryHours) {

        FileRecord file = fileRepository.findByFileUuid(fileUuid)
                .orElseThrow(() -> new RuntimeException("File not found"));

        if (file.getOwnerId().longValue() != ownerId)
            throw new RuntimeException("You don't own this file");

        if (ownerEmail.equalsIgnoreCase(recipientEmail.trim()))
            throw new RuntimeException("You can't share a file with yourself");

        Map<String, Object> recipient = lookupVaultUser(recipientEmail);
        if (recipient == null)
            throw new RuntimeException("No vault account found for: " + recipientEmail);

        String recipientName       = (String) recipient.get("fullName");
        String recipientEmailClean = (String) recipient.get("email");
        long   recipientOwnerId    = ((Number) recipient.get("ownerId")).longValue();

        // Calculate expiry
        LocalDateTime expiresAt = (expiryHours > 0)
                ? LocalDateTime.now().plusHours(expiryHours)
                : null;

        Optional<FileShare> existing = shareRepository
                .findByFileUuidAndSharedWithEmail(fileUuid, recipientEmailClean);

        if (existing.isPresent()) {
            FileShare ex = existing.get();
            if (ex.getActive() && !ex.isExpired())
                throw new RuntimeException("Already shared with " + recipientName);
            // Re-activate with new expiry
            ex.setActive(true);
            ex.setExpiresAt(expiresAt);
            log.info("🔄 Share re-activated: {} → {}", fileUuid, recipientEmailClean);
            return shareRepository.save(ex);
        }

        FileShare share = FileShare.builder()
                .fileUuid(fileUuid)
                .ownerId(ownerId)
                .ownerEmail(ownerEmail)
                .sharedWithEmail(recipientEmailClean)
                .sharedWithOwnerId(recipientOwnerId)
                .sharedWithName(recipientName)
                .expiresAt(expiresAt)
                .active(true)
                .build();

        share = shareRepository.save(share);
        log.info("✅ File shared: {} → {} (expires: {})", fileUuid, recipientEmailClean,
                expiresAt != null ? expiresAt : "never");
        return share;
    }

    // ══════════════════════════════════════════
    //  GET all people a file is shared with
    // ══════════════════════════════════════════
    public List<FileShare> getSharesForFile(String fileUuid, Long ownerId) {
        return shareRepository.findByFileUuidAndOwnerIdOrderBySharedAtDesc(fileUuid, ownerId);
    }

    public List<FileShare> getFilesSharedWithMe(Long myOwnerId) {
        return shareRepository.findBySharedWithOwnerIdAndActiveTrue(myOwnerId)
                .stream()
                .filter(s -> !s.isExpired())
                .toList();
    }

    public List<FileShare> getFilesSharedByMe(Long ownerId) {
        return shareRepository.findByOwnerIdOrderBySharedAtDesc(ownerId);
    }

    // ══════════════════════════════════════════
    //  REVOKE a share
    // ══════════════════════════════════════════
    @Transactional
    public void revokeShare(Long shareId, Long ownerId) {
        FileShare share = shareRepository.findById(shareId)
                .orElseThrow(() -> new RuntimeException("Share not found"));
        if (share.getOwnerId().longValue() != ownerId)
            throw new RuntimeException("You don't own this share");
        share.setActive(false);
        shareRepository.save(share);
        log.info("🚫 Share revoked: id={}", shareId);
    }

    // ══════════════════════════════════════════
    //  CHECK access (owner OR valid share)
    // ══════════════════════════════════════════
    public boolean hasAccess(String fileUuid, Long requesterId) {
        FileRecord file = fileRepository.findByFileUuid(fileUuid).orElse(null);
        if (file == null) return false;
        if (file.getOwnerId().longValue() == requesterId) return true;

        return shareRepository.findByFileUuidAndActiveTrue(fileUuid)
                .stream()
                .anyMatch(s -> s.getSharedWithOwnerId().longValue() == requesterId
                               && !s.isExpired());
    }

    // ══════════════════════════════════════════
    //  SCHEDULER — auto-revoke expired shares
    //  Runs every 30 minutes
    // ══════════════════════════════════════════
    @Scheduled(fixedDelay = 1_800_000)
    @Transactional
    public void revokeExpiredShares() {
        List<FileShare> expired = shareRepository.findExpiredActiveShares(LocalDateTime.now());
        if (expired.isEmpty()) return;
        expired.forEach(s -> s.setActive(false));
        shareRepository.saveAll(expired);
        log.info("⏰ Auto-revoked {} expired shares", expired.size());
    }
}
