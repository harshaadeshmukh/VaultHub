package com.vaulthub.file.controller;

import com.vaulthub.file.dto.FileResponse;
import com.vaulthub.file.dto.SharedFileView;
import com.vaulthub.file.entity.ChunkRecord;
import com.vaulthub.file.entity.FileRecord;
import com.vaulthub.file.service.FileService;
import com.vaulthub.file.service.ActivityLogService;
import com.vaulthub.file.service.ShareService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

@Slf4j
@Controller
@RequiredArgsConstructor
public class FileController {

    private static final String ADMIN_EMAIL = "harshad@gmail.com";

    private final FileService        fileService;
    private final ShareService       shareService;
    private final ActivityLogService activityLogService;

    // ══════════════════════════════════════════
    //  GET /files
    // ══════════════════════════════════════════
    @GetMapping("/files")
    public String filesPage(
            @AuthenticationPrincipal UserDetails userDetails,
            HttpSession session,
            Model model) {

        Long ownerId = getUserId(userDetails);

        List<FileResponse> files = fileService.getFilesForUser(ownerId);
        long totalBytes = fileService.getTotalStorageBytes(ownerId);

        List<SharedFileView> sharedWithMe = shareService
                .getFilesSharedWithMe(ownerId)
                .stream()
                .map(share -> {
                    try {
                        FileResponse f = fileService.getFile(share.getFileUuid());
                        return SharedFileView.builder()
                                .shareId(share.getId())
                                .ownerEmail(share.getOwnerEmail())
                                .sharedAt(share.getSharedAt())
                                .fileUuid(f.getFileUuid())
                                .fileName(f.getFileName())
                                .fileSizeFormatted(f.getFileSizeFormatted())
                                .mimeType(f.getMimeType())
                                .build();
                    } catch (Exception e) {
                        log.warn("Could not resolve shared file: {}", share.getFileUuid());
                        return null;
                    }
                })
                .filter(v -> v != null)
                .toList();

        model.addAttribute("files",        files);
        model.addAttribute("totalBytes",   totalBytes);
        model.addAttribute("fileCount",    files.size());
        model.addAttribute("sharedWithMe", sharedWithMe);
        model.addAttribute("sharedCount",  sharedWithMe.size());
        model.addAttribute("displayName",  getDisplayName(userDetails, session));
        model.addAttribute("vaultId",      getVaultId(userDetails));
        model.addAttribute("isAdmin",      Boolean.TRUE.equals(session.getAttribute("isAdmin")));
        model.addAttribute("profilePhoto", session.getAttribute("profilePhoto"));
        return "files";
    }

    // ══════════════════════════════════════════
    //  GET /files/upload
    // ══════════════════════════════════════════
    @GetMapping("/files/upload")
    public String uploadPage(
            @AuthenticationPrincipal UserDetails userDetails,
            HttpSession session,
            Model model) {
        model.addAttribute("displayName", getDisplayName(userDetails, session));
        model.addAttribute("vaultId",     getVaultId(userDetails));
        model.addAttribute("isAdmin",     Boolean.TRUE.equals(session.getAttribute("isAdmin")));
        model.addAttribute("profilePhoto", session.getAttribute("profilePhoto"));
        return "upload";
    }

    // ══════════════════════════════════════════
    //  POST /files/upload
    // ══════════════════════════════════════════
    @PostMapping("/files/upload")
    public void handleUpload(
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal UserDetails userDetails,
            HttpServletRequest request,
            jakarta.servlet.http.HttpServletResponse response,
            RedirectAttributes redirectAttributes) throws IOException {

        if (file.isEmpty()) {
            response.sendRedirect(buildRedirectUrl(request, "/files/upload?error=empty"));
            return;
        }
        try {
            Long ownerId = getUserId(userDetails);
            FileResponse uploaded = fileService.uploadFile(file, ownerId);
            log.info("✅ Upload success: {}", uploaded.getFileName());
            response.sendRedirect(buildRedirectUrl(request, "/files?success=uploaded"));
        } catch (IOException e) {
            log.error("Upload failed", e);
            response.sendRedirect(buildRedirectUrl(request, "/files/upload?error=failed"));
        }
    }

    // ══════════════════════════════════════════
    //  GET /files/view/{uuid}  — USER
    // ══════════════════════════════════════════
    @GetMapping("/files/view/{uuid}")
    public String viewerPage(
            @PathVariable String uuid,
            @AuthenticationPrincipal UserDetails userDetails,
            Model model) {

        if (!shareService.hasAccess(uuid, getUserId(userDetails))) {
            return "redirect:/files?error=noaccess";
        }
        FileResponse meta = fileService.getFile(uuid);
        model.addAttribute("file",       meta);
        model.addAttribute("viewerType", getViewerType(meta.getMimeType()));
        activityLogService.logView(getUserId(userDetails), meta.getFileName(), uuid);
        model.addAttribute("streamUrl",  "/files/stream/" + uuid);
        return "viewer";
    }

    // ══════════════════════════════════════════
    //  GET /files/stream/{uuid}  — USER
    // ══════════════════════════════════════════
    @GetMapping("/files/stream/{uuid}")
    public ResponseEntity<StreamingResponseBody> streamFile(
            @PathVariable String uuid,
            @AuthenticationPrincipal UserDetails userDetails) {

        if (!shareService.hasAccess(uuid, getUserId(userDetails))) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        try {
            FileRecord meta = fileService.getFileRecord(uuid);
            List<ChunkRecord> chunks = fileService.getChunks(meta.getId());
            StreamingResponseBody body = out -> {
                try (BufferedOutputStream buf = new BufferedOutputStream(out, 65536)) {
                    for (ChunkRecord chunk : chunks) {
                        Files.copy(Paths.get(chunk.getStoragePath()), buf);
                    }
                    buf.flush();
                }
            };
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(meta.getMimeType()))
                    .contentLength(meta.getFileSize())
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + meta.getFileName() + "\"")
                    .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                    .header(HttpHeaders.CACHE_CONTROL, "no-cache")
                    .body(body);
        } catch (Exception e) {
            log.error("Stream failed: {}", uuid, e);
            return ResponseEntity.notFound().build();
        }
    }

    // ══════════════════════════════════════════
    //  GET /files/download/{uuid}  — USER
    // ══════════════════════════════════════════
    @GetMapping("/files/download/{uuid}")
    public ResponseEntity<StreamingResponseBody> downloadFile(
            @PathVariable String uuid,
            @AuthenticationPrincipal UserDetails userDetails) {

        if (!shareService.hasAccess(uuid, getUserId(userDetails))) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        try {
            FileRecord meta = fileService.getFileRecord(uuid);
            List<ChunkRecord> chunks = fileService.getChunks(meta.getId());
            StreamingResponseBody body = out -> {
                try (BufferedOutputStream buf = new BufferedOutputStream(out, 65536)) {
                    for (ChunkRecord chunk : chunks) {
                        Files.copy(Paths.get(chunk.getStoragePath()), buf);
                    }
                    buf.flush();
                }
            };
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(meta.getMimeType()))
                    .contentLength(meta.getFileSize())
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + meta.getFileName() + "\"")
                    .body(body);
        } catch (Exception e) {
            log.error("Download failed: {}", uuid, e);
            return ResponseEntity.notFound().build();
        }
    }

    // ══════════════════════════════════════════
    //  POST /files/delete/{uuid}
    // ══════════════════════════════════════════
    @PostMapping("/files/delete/{uuid}")
    public void deleteFile(
            @PathVariable String uuid,
            @AuthenticationPrincipal UserDetails userDetails,
            HttpServletRequest request,
            jakarta.servlet.http.HttpServletResponse response) throws IOException {

        try {
            fileService.deleteFile(uuid, getUserId(userDetails));
            response.sendRedirect(buildRedirectUrl(request, "/files?success=deleted"));
        } catch (Exception e) {
            log.error("Delete failed: {}", uuid, e);
            response.sendRedirect(buildRedirectUrl(request, "/files?error=deletefailed"));
        }
    }

    // ══════════════════════════════════════════
    //  POST /files/rename/{uuid}
    // ══════════════════════════════════════════
    @PostMapping("/files/rename/{uuid}")
    @ResponseBody
    public ResponseEntity<?> renameFile(
            @PathVariable String uuid,
            @RequestParam String newName,
            @AuthenticationPrincipal UserDetails userDetails) {

        try {
            Long ownerId = getUserId(userDetails);
            FileResponse updated = fileService.renameFile(uuid, ownerId, newName);
            activityLogService.log(ownerId,
                    com.vaulthub.file.entity.ActivityLog.ActivityType.UPLOAD,
                    updated.getFileName(), uuid, "Renamed");
            return ResponseEntity.ok(java.util.Map.of("success", true, "newName", updated.getFileName()));
        } catch (Exception e) {
            log.error("Rename failed: {}", uuid, e);
            return ResponseEntity.badRequest().body(java.util.Map.of("success", false, "error", e.getMessage()));
        }
    }

    // ══════════════════════════════════════════
    //  GET /admin/files/view/{uuid}  — ADMIN ONLY
    // ══════════════════════════════════════════
    @GetMapping("/admin/files/view/{uuid}")
    public String adminViewerPage(
            @PathVariable String uuid,
            @AuthenticationPrincipal UserDetails userDetails,
            Model model) {

        if (!ADMIN_EMAIL.equals(userDetails.getUsername())) {
            return "redirect:/files?error=noaccess";
        }
        FileResponse meta = fileService.getFile(uuid);
        model.addAttribute("file",       meta);
        model.addAttribute("viewerType", getViewerType(meta.getMimeType()));
        model.addAttribute("streamUrl",  "/admin/files/stream/" + uuid);
        return "viewer";
    }

    // ══════════════════════════════════════════
    //  GET /admin/files/stream/{uuid}  — ADMIN ONLY
    // ══════════════════════════════════════════
    @GetMapping("/admin/files/stream/{uuid}")
    public ResponseEntity<StreamingResponseBody> adminStreamFile(
            @PathVariable String uuid,
            @AuthenticationPrincipal UserDetails userDetails) {

        if (!ADMIN_EMAIL.equals(userDetails.getUsername())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        try {
            FileRecord meta = fileService.getFileRecord(uuid);
            List<ChunkRecord> chunks = fileService.getChunks(meta.getId());
            StreamingResponseBody body = out -> {
                try (BufferedOutputStream buf = new BufferedOutputStream(out, 65536)) {
                    for (ChunkRecord chunk : chunks) {
                        Files.copy(Paths.get(chunk.getStoragePath()), buf);
                    }
                    buf.flush();
                }
            };
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(meta.getMimeType()))
                    .contentLength(meta.getFileSize())
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + meta.getFileName() + "\"")
                    .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                    .header(HttpHeaders.CACHE_CONTROL, "no-cache")
                    .body(body);
        } catch (Exception e) {
            log.error("Admin stream failed: {}", uuid, e);
            return ResponseEntity.notFound().build();
        }
    }

    // ── Helpers ──────────────────────────────

    private String buildRedirectUrl(HttpServletRequest request, String path) {
        String proto = request.getHeader("X-Forwarded-Proto");
        String host  = request.getHeader("X-Forwarded-Host");
        String port  = request.getHeader("X-Forwarded-Port");
        if (proto != null && host != null) {
            if (port != null && !port.equals("80") && !port.equals("443"))
                return proto + "://" + host + ":" + port + path;
            return proto + "://" + host + path;
        }
        return path;
    }

    private String getViewerType(String mimeType) {
        if (mimeType == null)                   return "download";
        if (mimeType.startsWith("image/"))      return "image";
        if (mimeType.equals("application/pdf")) return "pdf";
        if (mimeType.startsWith("video/"))      return "video";
        if (mimeType.startsWith("audio/"))      return "audio";
        if (mimeType.startsWith("text/"))       return "text";
        return "download";
    }

    private String getDisplayName(UserDetails userDetails, HttpSession session) {
        String fullName = (String) session.getAttribute("fullName");
        if (fullName != null && !fullName.isBlank()) return fullName;
        String email = userDetails.getUsername();
        String name  = email.contains("@") ? email.substring(0, email.indexOf('@')) : email;
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

    private Long getUserId(UserDetails userDetails) {
        return (long) userDetails.getUsername().hashCode();
    }

    private String getVaultId(UserDetails userDetails) {
        return "vault-" + Integer.toHexString(Math.abs(userDetails.getUsername().hashCode()));
    }

    private String getVaultIdFromSession(UserDetails userDetails, HttpSession session) {
        String vaultId = (String) session.getAttribute("vaultId");
        if (vaultId != null && !vaultId.isBlank()) return vaultId;
        return getVaultId(userDetails);
    }
}
