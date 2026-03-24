package com.vaulthub.file.controller;

import com.vaulthub.file.dto.FileResponse;
import com.vaulthub.file.dto.SharedFileView;
import com.vaulthub.file.entity.FileShare;
import com.vaulthub.file.service.FileService;
import com.vaulthub.file.service.ActivityLogService;
import com.vaulthub.file.service.ShareService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Controller
@RequiredArgsConstructor
public class ShareController {

    private final ShareService shareService;
    private final ActivityLogService activityLogService;
    private final FileService fileService;

    // ══════════════════════════════════════════
    //  GET /api/share/lookup?email=...
    //  Called by Share modal JS to check if the
    //  typed email belongs to a vault member
    // ══════════════════════════════════════════
    @GetMapping("/api/share/lookup")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> lookupUser(
            @RequestParam String email) {

        Map<String, Object> result = shareService.lookupVaultUser(email);
        if (result == null) {
            return ResponseEntity.ok(Map.of("found", false));
        }
        return ResponseEntity.ok(result);
    }

    // ══════════════════════════════════════════
    //  POST /files/share/send
    //  Owner shares a file with a specific user
    // ══════════════════════════════════════════
    @PostMapping("/files/share/send")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> shareFile(
            @RequestParam String fileUuid,
            @RequestParam String recipientEmail,
            @RequestParam(defaultValue = "0") int expiryHours,
            @AuthenticationPrincipal UserDetails userDetails) {

        try {
            Long ownerId   = getUserId(userDetails);
            String ownerEmail = userDetails.getUsername();

            FileShare share = shareService.shareFile(
                    fileUuid, ownerId, ownerEmail, recipientEmail, expiryHours);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "File shared with " + share.getSharedWithName()
            ));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of(
                    "success", false,
                    "message", e.getMessage()
            ));
        }
    }

    // ══════════════════════════════════════════
    //  GET /files/shared
    //  Two tabs: "Shared by me" + "Shared with me"
    // ══════════════════════════════════════════
    @GetMapping("/files/shared")
    public String sharedPage(
            @AuthenticationPrincipal UserDetails userDetails,
            HttpSession session,
            Model model) {

        Long ownerId = getUserId(userDetails);

        // ── Tab 1: Files I shared with others ─────────────
        // List<Map<String,Object>> — each map: { "share": FileShare, "file": FileResponse }
        // shared.html uses th:with="share=${entry['share']}, file=${entry['file']}"
        List<Map<String, Object>> sharedByMe = shareService
                .getFilesSharedByMe(ownerId)
                .stream()
                .map(s -> {
                    try {
                        FileResponse f = fileService.getFile(s.getFileUuid());
                        return (Map<String, Object>) Map.of("share", s, "file", f);
                    } catch (Exception e) {
                        return null;
                    }
                })
                .filter(m -> m != null)
                .toList();

        // ── Tab 2: Files others shared WITH me ────────────
        // Flatten into SharedFileView so Thymeleaf can iterate
        // without needing index access
        List<SharedFileView> sharedWithMe = shareService
                .getFilesSharedWithMe(ownerId)
                .stream()
                .map(s -> {
                    try {
                        FileResponse f = fileService.getFile(s.getFileUuid());
                        return SharedFileView.builder()
                                .shareId(s.getId())
                                .ownerEmail(s.getOwnerEmail())
                                .sharedAt(s.getSharedAt())
                                .fileUuid(f.getFileUuid())
                                .fileName(f.getFileName())
                                .fileSizeFormatted(f.getFileSizeFormatted())
                                .mimeType(f.getMimeType())
                                .build();
                    } catch (Exception e) {
                        return null;
                    }
                })
                .filter(v -> v != null)
                .toList();

        model.addAttribute("sharedByMe",   sharedByMe);
        model.addAttribute("sharedWithMe", sharedWithMe);
        model.addAttribute("displayName",  getDisplayName(userDetails, session));
        model.addAttribute("vaultId",      getVaultId(userDetails));
        model.addAttribute("fileCount",    0);
        model.addAttribute("profilePhoto", session.getAttribute("profilePhoto"));

        return "shared";
    }

    // ══════════════════════════════════════════
    //  POST /files/share/revoke/{shareId}
    // ══════════════════════════════════════════
    @PostMapping("/files/share/revoke/{shareId}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> revokeShare(
            @PathVariable Long shareId,
            @AuthenticationPrincipal UserDetails userDetails) {
        Map<String, Object> resp = new HashMap<>();
        try {
            shareService.revokeShare(shareId, getUserId(userDetails));
            resp.put("success", true);
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            resp.put("success", false);
            resp.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(resp);
        }
    }

    // ── Helpers ──────────────────────────────

    private Long getUserId(UserDetails u) {
        return (long) u.getUsername().hashCode();
    }

    private String getVaultId(UserDetails u) {
        String hash = Integer.toHexString(Math.abs(u.getUsername().hashCode()));
        return "vault-" + hash;
    }

    private String getDisplayName(UserDetails u, HttpSession s) {
        String name = (String) s.getAttribute("fullName");
        if (name != null && !name.isBlank()) return name;
        String email = u.getUsername();
        String n = email.contains("@") ? email.substring(0, email.indexOf('@')) : email;
        return Character.toUpperCase(n.charAt(0)) + n.substring(1);
    }

    private String buildRedirectUrl(HttpServletRequest req, String path) {
        String proto = req.getHeader("X-Forwarded-Proto");
        String host  = req.getHeader("X-Forwarded-Host");
        String port  = req.getHeader("X-Forwarded-Port");
        if (proto != null && host != null) {
            if (port != null && !port.equals("80") && !port.equals("443"))
                return proto + "://" + host + ":" + port + path;
            return proto + "://" + host + path;
        }
        return path;
    }
}