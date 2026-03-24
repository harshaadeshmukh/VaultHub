package com.vaulthub.auth.controller;

import com.vaulthub.auth.entity.User;
import com.vaulthub.auth.repository.UserRepository;
import com.vaulthub.auth.service.AuthService;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.util.Base64;

@Slf4j
@Controller
@RequiredArgsConstructor
public class SettingsController {

    private final AuthService authService;
    private final PasswordEncoder passwordEncoder;
    private final UserRepository userRepository;

    // ── Show settings page ──
    @GetMapping("/settings")
    public String settingsPage(
            @AuthenticationPrincipal UserDetails userDetails,
            HttpSession session,
            Model model) {

        User user = authService.findByEmail(userDetails.getUsername());
        model.addAttribute("user", user);
        model.addAttribute("isAdmin", "harshad@gmail.com".equals(user.getEmail()));

        // Keep session in sync with DB photo (e.g. after login)
        if (user.getProfilePhoto() != null && !user.getProfilePhoto().isBlank()) {
            session.setAttribute("profilePhoto", user.getProfilePhoto());
        }
        return "settings";
    }

    // ── Update display name ──
    @PostMapping("/settings/update-name")
    public String updateName(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam String fullName,
            HttpSession session,
            RedirectAttributes ra) {

        if (fullName == null || fullName.trim().length() < 2) {
            ra.addFlashAttribute("nameError", "Name must be at least 2 characters.");
            return "redirect:/settings";
        }

        User user = authService.findByEmail(userDetails.getUsername());
        user.setFullName(fullName.trim());
        authService.saveUser(user);

        // Update session so navbar reflects new name immediately
        session.setAttribute("fullName", fullName.trim());

        ra.addFlashAttribute("nameSuccess", "Name updated successfully!");
        log.info("✏️ Name updated for {}", userDetails.getUsername());
        return "redirect:/settings";
    }

    // ── Change password ──
    @PostMapping("/settings/change-password")
    public String changePassword(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam String currentPassword,
            @RequestParam String newPassword,
            @RequestParam String confirmPassword,
            RedirectAttributes ra) {

        User user = authService.findByEmail(userDetails.getUsername());

        // Verify current password
        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            ra.addFlashAttribute("pwError", "Current password is incorrect.");
            return "redirect:/settings";
        }

        if (newPassword.length() < 6) {
            ra.addFlashAttribute("pwError", "New password must be at least 6 characters.");
            return "redirect:/settings";
        }

        if (!newPassword.equals(confirmPassword)) {
            ra.addFlashAttribute("pwError", "New passwords do not match.");
            return "redirect:/settings";
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        authService.saveUser(user);

        ra.addFlashAttribute("pwSuccess", "Password changed successfully!");
        log.info("🔑 Password changed for {}", userDetails.getUsername());
        return "redirect:/settings";
    }

    // ── Remove profile photo ──
    @PostMapping("/settings/remove-photo")
    public String removePhoto(
            @AuthenticationPrincipal UserDetails userDetails,
            HttpSession session,
            RedirectAttributes ra) {
        try {
            User user = authService.findByEmail(userDetails.getUsername());
            user.setProfilePhoto(null);
            authService.saveUser(user);
            session.removeAttribute("profilePhoto");
            ra.addFlashAttribute("photoSuccess", "Profile photo removed.");
            log.info("🗑️ Profile photo removed for {}", userDetails.getUsername());
        } catch (Exception e) {
            log.error("Photo removal failed: {}", e.getMessage());
            ra.addFlashAttribute("photoError", "Could not remove photo. Try again.");
        }
        return "redirect:/settings";
    }

    // ── Upload profile photo ──
    @PostMapping("/settings/upload-photo")
    public String uploadPhoto(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam("photo") MultipartFile photo,
            HttpSession session,
            RedirectAttributes ra) {

        if (photo == null || photo.isEmpty()) {
            ra.addFlashAttribute("photoError", "No file selected.");
            return "redirect:/settings";
        }

        String contentType = photo.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            ra.addFlashAttribute("photoError", "Only image files are allowed.");
            return "redirect:/settings";
        }

        if (photo.getSize() > 5 * 1024 * 1024) {
            ra.addFlashAttribute("photoError", "Image must be smaller than 5 MB.");
            return "redirect:/settings";
        }

        try {
            String base64 = Base64.getEncoder().encodeToString(photo.getBytes());
            String dataUri = "data:" + contentType + ";base64," + base64;

            User user = authService.findByEmail(userDetails.getUsername());
            user.setProfilePhoto(dataUri);
            authService.saveUser(user);

            // Store in session so all microservices can show it in topbar
            session.setAttribute("profilePhoto", dataUri);

            ra.addFlashAttribute("photoSuccess", "Profile photo updated!");
            log.info("🖼️ Profile photo uploaded for {}", userDetails.getUsername());
        } catch (Exception e) {
            log.error("Photo upload failed: {}", e.getMessage());
            ra.addFlashAttribute("photoError", "Upload failed. Please try again.");
        }

        return "redirect:/settings";
    }

    // ── Delete own account ──
    @PostMapping("/settings/delete-account")
    public String deleteAccount(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam String confirmPassword,
            jakarta.servlet.http.HttpServletRequest request,
            RedirectAttributes ra) {

        com.vaulthub.auth.entity.User user = authService.findByEmail(userDetails.getUsername());

        if (user == null) {
            ra.addFlashAttribute("deleteError", "User not found.");
            return "redirect:/settings";
        }

        if (!passwordEncoder.matches(confirmPassword, user.getPassword())) {
            ra.addFlashAttribute("deleteError", "Incorrect password. Account not deleted.");
            return "redirect:/settings";
        }

        // Delete all files from file service first
        long ownerId = (long) user.getEmail().hashCode();
        try {
            SimpleClientHttpRequestFactory fac = new SimpleClientHttpRequestFactory();
            fac.setConnectTimeout(4000);
            fac.setReadTimeout(4000);
            RestTemplate rt = new RestTemplate(fac);
            rt.delete("http://localhost:8082/api/admin/user-files/" + ownerId);
        } catch (Exception e) {
            log.warn("Could not delete files during account deletion: {}", e.getMessage());
        }

        // Delete user from DB
        userRepository.delete(user);
        log.info("💀 Account deleted: {}", user.getEmail());

        // Invalidate session cleanly then redirect to home
        try {
            SecurityContextHolder.clearContext();
            request.getSession(false);
            request.logout();
        } catch (Exception e) {
            log.warn("Logout error during account deletion: {}", e.getMessage());
        }

        return "redirect:/login?accountDeleted=true";
    }
}
