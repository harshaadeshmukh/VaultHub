package com.vaulthub.auth.controller;

import com.vaulthub.auth.entity.User;
import com.vaulthub.auth.repository.UserRepository;
import com.vaulthub.auth.service.AuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.Duration;
import java.util.*;

@Slf4j
@Controller
@RequiredArgsConstructor
public class AdminController {

    private final UserRepository userRepository;
    private final AuthService authService;

    private RestTemplate rt() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(4000);
        factory.setReadTimeout(4000);
        return new RestTemplate(factory);
    }

    private static final String ADMIN_EMAIL = "harshad@gmail.com";

    private boolean isAdmin(UserDetails u) {
        // Only this specific email can access the admin panel
        return ADMIN_EMAIL.equals(u.getUsername());
    }

    // ══════════════════════════════════════════════
    //  GET /admin  — Admin dashboard
    // ══════════════════════════════════════════════
    @GetMapping("/admin")
    public String adminPanel(
            @AuthenticationPrincipal UserDetails userDetails,
            Model model) {

        if (!isAdmin(userDetails)) return "redirect:/dashboard";

        User adminUser = authService.findByEmail(userDetails.getUsername());
        List<User> users = userRepository.findAll();

        // Fetch per-user file stats from file service
        Map<String, Map<String, Object>> rawStats = new HashMap<>();
        try {
            ResponseEntity<Map<String, Map<String, Object>>> resp = rt().exchange(
                    "http://localhost:8082/api/admin/user-stats",
                    HttpMethod.GET, null,
                    new ParameterizedTypeReference<>() {});
            if (resp.getBody() != null) rawStats = resp.getBody();
        } catch (Exception e) {
            log.warn("Could not fetch file stats from file service: {}", e.getMessage());
        }

        // Build typed view models using AdminUserRow
        List<AdminUserRow> userRows = new ArrayList<>();
        long totalStorage = 0;
        int totalFiles = 0;

        for (User u : users) {
            long ownerId = (long) u.getEmail().hashCode();
            String key = String.valueOf(ownerId);
            Map<String, Object> stats = rawStats.getOrDefault(key, Map.of());

            int fileCount = stats.containsKey("fileCount")
                    ? ((Number) stats.get("fileCount")).intValue() : 0;
            long bytes = stats.containsKey("totalBytes")
                    ? ((Number) stats.get("totalBytes")).longValue() : 0L;

            totalFiles   += fileCount;
            totalStorage += bytes;

            userRows.add(new AdminUserRow(
                    u.getId(),
                    u.getFullName(),
                    u.getEmail(),
                    u.getVaultId(),
                    u.getRole().name(),
                    u.getCreatedAt() != null ? u.getCreatedAt().toString() : "—",
                    fileCount,
                    bytes,
                    String.format("%.1f", bytes / 1048576.0),
                    u.getEmail().equals(userDetails.getUsername()),
                    u.getProfilePhoto()
            ));
        }

        model.addAttribute("userRows",     userRows);
        model.addAttribute("totalUsers",   users.size());
        model.addAttribute("totalStorage", String.format("%.1f", totalStorage / 1048576.0));
        model.addAttribute("adminName",    adminUser.getFullName());
        model.addAttribute("adminInitial", adminUser.getFullName().substring(0, 1).toUpperCase());
        model.addAttribute("adminProfilePhoto", adminUser.getProfilePhoto() != null ? adminUser.getProfilePhoto() : "");

        return "admin";
    }

    // ══════════════════════════════════════════════
    //  GET /admin/users/{id}  — User detail page
    // ══════════════════════════════════════════════
    @GetMapping("/admin/users/{id}")
    public String userDetail(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails,
            Model model) {

        if (!isAdmin(userDetails)) return "redirect:/dashboard";

        User u = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found: " + id));

        long ownerId = (long) u.getEmail().hashCode();

        // Fetch this user's files from file service
        List<Map<String, Object>> files = new ArrayList<>();
        long totalBytes = 0;
        try {
            ResponseEntity<List<Map<String, Object>>> resp = rt().exchange(
                    "http://localhost:8082/api/admin/user-files-list/" + ownerId,
                    HttpMethod.GET, null,
                    new ParameterizedTypeReference<>() {});
            if (resp.getBody() != null) {
                files = resp.getBody();
                for (Map<String, Object> f : files) {
                    Object s = f.get("fileSize");
                    if (s instanceof Number n) totalBytes += n.longValue();
                }
            }
        } catch (Exception e) {
            log.warn("Could not fetch files for user {}: {}", u.getEmail(), e.getMessage());
        }

        User adminUser = authService.findByEmail(userDetails.getUsername());
        model.addAttribute("u",            u);
        model.addAttribute("ownerId",      ownerId);
        model.addAttribute("files",        files);
        model.addAttribute("fileCount",    files.size());
        model.addAttribute("totalBytes",   totalBytes);
        model.addAttribute("storageMB",    String.format("%.2f", totalBytes / 1048576.0));
        model.addAttribute("adminName",    adminUser.getFullName());
        model.addAttribute("adminInitial", adminUser.getFullName().substring(0,1).toUpperCase());
        model.addAttribute("adminProfilePhoto", adminUser.getProfilePhoto() != null ? adminUser.getProfilePhoto() : "");
        model.addAttribute("isSelf",       u.getEmail().equals(userDetails.getUsername()));

        return "admin-user-detail";
    }

    // /admin/files is removed — redirect to admin panel
    @GetMapping("/admin/files")
    public String allFilesRedirect() {
        return "redirect:/admin";
    }
    @PostMapping("/admin/users/{id}/role")
    public String changeRole(
            @PathVariable Long id,
            @RequestParam String role,
            @AuthenticationPrincipal UserDetails userDetails,
            RedirectAttributes ra) {

        if (!isAdmin(userDetails)) return "redirect:/dashboard";

        userRepository.findById(id).ifPresent(u -> {
            if (!u.getEmail().equals(userDetails.getUsername())) {
                u.setRole(User.Role.valueOf(role));
                userRepository.save(u);
                log.info("🔑 Role of {} changed to {}", u.getEmail(), role);
            }
        });
        ra.addFlashAttribute("flashMsg", "Role updated successfully.");
        return "redirect:/admin";
    }

    // ══════════════════════════════════════════════
    //  POST /admin/users/{id}/delete
    // ══════════════════════════════════════════════
    @PostMapping("/admin/users/{id}/delete")
    public String deleteUser(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails,
            RedirectAttributes ra) {

        if (!isAdmin(userDetails)) return "redirect:/dashboard";

        userRepository.findById(id).ifPresent(u -> {
            if (!u.getEmail().equals(userDetails.getUsername())) {
                long ownerId = (long) u.getEmail().hashCode();
                try {
                    rt().delete("http://localhost:8082/api/admin/user-files/" + ownerId);
                } catch (Exception e) {
                    log.warn("Could not delete files for user {}: {}", u.getEmail(), e.getMessage());
                }
                userRepository.delete(u);
                log.info("🗑️ Admin deleted user: {}", u.getEmail());
            }
        });
        ra.addFlashAttribute("flashMsg", "User deleted.");
        return "redirect:/admin";
    }

    // ── Simple typed DTO to avoid Map<String,Object> Thymeleaf issues ──
    public static class AdminUserRow {
        public final Long   id;
        public final String fullName;
        public final String email;
        public final String vaultId;
        public final String role;
        public final String createdAt;
        public final int    fileCount;
        public final long   totalBytes;
        public final String storageMB;
        public final boolean isSelf;
        public final String profilePhoto;

        public AdminUserRow(Long id, String fullName, String email, String vaultId,
                            String role, String createdAt,
                            int fileCount, long totalBytes, String storageMB,
                            boolean isSelf, String profilePhoto) {
            this.id = id; this.fullName = fullName; this.email = email;
            this.vaultId = vaultId; this.role = role; this.createdAt = createdAt;
            this.fileCount = fileCount; this.totalBytes = totalBytes;
            this.storageMB = storageMB; this.isSelf = isSelf;
            this.profilePhoto = profilePhoto != null ? profilePhoto : "";
        }

        public Long   getId()           { return id; }
        public String getFullName()     { return fullName; }
        public String getEmail()        { return email; }
        public String getVaultId()      { return vaultId; }
        public String getRole()         { return role; }
        public String getCreatedAt()    { return createdAt; }
        public int    getFileCount()    { return fileCount; }
        public long   getTotalBytes()   { return totalBytes; }
        public String getStorageMB()    { return storageMB; }
        public boolean isSelf()         { return isSelf; }
        public String getProfilePhoto() { return profilePhoto; }
        public String getInitial()      { return fullName.isEmpty() ? "?" : fullName.substring(0,1).toUpperCase(); }
        public boolean isAdmin()        { return "ADMIN".equals(role); }
        public boolean isUser()         { return "USER".equals(role); }
        public int getBarWidth() {
            if (totalBytes <= 0) return 0;
            long cap = 10L * 1024 * 1024 * 1024;
            return (int) Math.min(totalBytes * 100 / cap, 100);
        }
    }
}
