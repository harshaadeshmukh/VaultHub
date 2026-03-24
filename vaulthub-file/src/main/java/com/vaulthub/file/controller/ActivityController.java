package com.vaulthub.file.controller;

import com.vaulthub.file.entity.ActivityLog;
import com.vaulthub.file.entity.ActivityLog.ActivityType;
import com.vaulthub.file.entity.FileRecord;
import com.vaulthub.file.entity.FileShare;
import com.vaulthub.file.repository.FileRepository;
import com.vaulthub.file.repository.FileShareRepository;
import com.vaulthub.file.service.ActivityLogService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

@Slf4j
@Controller
@RequiredArgsConstructor
public class ActivityController {

    private final ActivityLogService  activityLogService;
    private final FileRepository      fileRepository;
    private final FileShareRepository fileShareRepository;

    @GetMapping("/activity")
    public String activityPage(
            @AuthenticationPrincipal UserDetails userDetails,
            HttpSession session,
            Model model) {

        Long ownerId = (long) userDetails.getUsername().hashCode();

        // ── Always backfill missing UPLOAD entries from file_records ──
        // This is idempotent — it checks if a log already exists per fileUuid
        try {
            List<FileRecord> files = fileRepository.findByOwnerIdOrderByCreatedAtDesc(ownerId);
            for (FileRecord f : files) {
                // Only seed if no log exists for this specific file uuid yet
                boolean alreadyLogged = activityLogService.existsForFile(ownerId, f.getFileUuid());
                if (!alreadyLogged) {
                    activityLogService.log(ownerId, ActivityType.UPLOAD,
                            f.getFileName(), f.getFileUuid(), null);
                }
            }

            // Backfill shares sent
            List<FileShare> sharesSent = fileShareRepository.findByOwnerIdOrderBySharedAtDesc(ownerId);
            for (FileShare s : sharesSent) {
                boolean alreadyLogged = activityLogService.existsShareLog(ownerId, s.getFileUuid(), s.getSharedWithEmail());
                if (!alreadyLogged) {
                    activityLogService.log(ownerId, ActivityType.SHARE_SENT,
                            s.getFileUuid(), s.getFileUuid(), s.getSharedWithEmail());
                }
            }
        } catch (Exception e) {
            log.warn("Activity backfill error: {}", e.getMessage());
        }

        List<ActivityLog> logs = activityLogService.getRecent(ownerId, 100);
        long totalActions = activityLogService.count(ownerId);

        String displayName = getDisplayName(userDetails, session);
        String vaultId = "vault-" + Integer.toHexString(Math.abs(userDetails.getUsername().hashCode()));

        model.addAttribute("logs",         logs);
        model.addAttribute("totalActions", totalActions);
        model.addAttribute("displayName",  displayName);
        model.addAttribute("vaultId",      vaultId);
        model.addAttribute("isAdmin",      Boolean.TRUE.equals(session.getAttribute("isAdmin")));
        model.addAttribute("profilePhoto", session.getAttribute("profilePhoto"));

        return "activity";
    }

    private String getDisplayName(UserDetails u, HttpSession s) {
        String name = (String) s.getAttribute("fullName");
        if (name != null && !name.isBlank()) return name;
        String email = u.getUsername();
        String n = email.contains("@") ? email.substring(0, email.indexOf('@')) : email;
        return Character.toUpperCase(n.charAt(0)) + n.substring(1);
    }
}
