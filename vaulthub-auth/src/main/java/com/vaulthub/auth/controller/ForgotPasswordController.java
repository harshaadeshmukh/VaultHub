package com.vaulthub.auth.controller;

import com.vaulthub.auth.entity.User;
import com.vaulthub.auth.repository.UserRepository;
import com.vaulthub.auth.service.OtpService;
import com.vaulthub.auth.service.OtpService.OtpResult;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@Slf4j
@Controller
@RequiredArgsConstructor
@RequestMapping("/forgot-password")
public class ForgotPasswordController {

    private final UserRepository userRepository;
    private final OtpService otpService;
    private final PasswordEncoder passwordEncoder;

    // ══════════════════════════════════════════════════════
    // STEP 1 — Enter email → send OTP
    // ══════════════════════════════════════════════════════

    @GetMapping
    public String forgotPage() {
        return "forgot-password";
    }

    @PostMapping("/send-otp")
    public String sendOtp(
            @RequestParam String email,
            HttpSession session,
            Model model) {

        Optional<User> userOpt = userRepository.findByEmail(email.trim().toLowerCase());
        if (userOpt.isEmpty()) {
            model.addAttribute("error", "No account found with that email address.");
            return "forgot-password";
        }

        User user = userOpt.get();
        try {
            otpService.sendOtp(user.getEmail(), user.getFullName());
        } catch (RuntimeException e) {
            model.addAttribute("error", e.getMessage());
            return "forgot-password";
        }

        // store email in session to pass between steps
        session.setAttribute("fp_email", user.getEmail());
        session.setAttribute("fp_verified", false);

        return "redirect:/forgot-password/verify";
    }

    // ══════════════════════════════════════════════════════
    // STEP 2 — Enter OTP
    // ══════════════════════════════════════════════════════

    @GetMapping("/verify")
    public String verifyPage(HttpSession session, Model model) {
        if (session.getAttribute("fp_email") == null) {
            return "redirect:/forgot-password";
        }
        model.addAttribute("email", maskEmail((String) session.getAttribute("fp_email")));
        return "forgot-password-verify";
    }

    @PostMapping("/verify-otp")
    public String verifyOtp(
            @RequestParam String otp,
            HttpSession session,
            Model model) {

        String email = (String) session.getAttribute("fp_email");
        if (email == null) return "redirect:/forgot-password";

        OtpResult result = otpService.verify(email, otp);

        switch (result) {
            case OK -> {
                session.setAttribute("fp_verified", true);
                return "redirect:/forgot-password/reset";
            }
            case WRONG -> {
                model.addAttribute("error", "Incorrect OTP. Please try again.");
                model.addAttribute("email", maskEmail(email));
                return "forgot-password-verify";
            }
            case EXPIRED -> {
                session.removeAttribute("fp_email");
                model.addAttribute("error", "OTP has expired. Please request a new one.");
                return "forgot-password";
            }
            case TOO_MANY -> {
                session.removeAttribute("fp_email");
                model.addAttribute("error", "Too many failed attempts. Please request a new OTP.");
                return "forgot-password";
            }
            default -> {
                model.addAttribute("error", "OTP not found. Please request a new one.");
                return "forgot-password";
            }
        }
    }

    // Resend OTP
    @PostMapping("/resend-otp")
    public String resendOtp(HttpSession session, Model model) {
        String email = (String) session.getAttribute("fp_email");
        if (email == null) return "redirect:/forgot-password";

        Optional<User> userOpt = userRepository.findByEmail(email);
        if (userOpt.isEmpty()) return "redirect:/forgot-password";

        try {
            otpService.sendOtp(email, userOpt.get().getFullName());
        } catch (RuntimeException e) {
            model.addAttribute("error", e.getMessage());
            model.addAttribute("email", maskEmail(email));
            return "forgot-password-verify";
        }

        model.addAttribute("success", "A new OTP has been sent to your email.");
        model.addAttribute("email", maskEmail(email));
        return "forgot-password-verify";
    }

    // ══════════════════════════════════════════════════════
    // STEP 3 — Set new password
    // ══════════════════════════════════════════════════════

    @GetMapping("/reset")
    public String resetPage(HttpSession session) {
        if (!Boolean.TRUE.equals(session.getAttribute("fp_verified"))) {
            return "redirect:/forgot-password";
        }
        return "forgot-password-reset";
    }

    @PostMapping("/reset")
    public String resetPassword(
            @RequestParam String password,
            @RequestParam String confirmPassword,
            HttpSession session,
            Model model) {

        if (!Boolean.TRUE.equals(session.getAttribute("fp_verified"))) {
            return "redirect:/forgot-password";
        }

        String email = (String) session.getAttribute("fp_email");
        if (email == null) return "redirect:/forgot-password";

        if (password.length() < 6) {
            model.addAttribute("error", "Password must be at least 6 characters.");
            return "forgot-password-reset";
        }
        if (!password.equals(confirmPassword)) {
            model.addAttribute("error", "Passwords do not match.");
            return "forgot-password-reset";
        }

        Optional<User> userOpt = userRepository.findByEmail(email);
        if (userOpt.isEmpty()) return "redirect:/forgot-password";

        User user = userOpt.get();
        user.setPassword(passwordEncoder.encode(password));
        userRepository.save(user);
        log.info("✅ Password reset for {}", email);

        // Clean up session
        session.removeAttribute("fp_email");
        session.removeAttribute("fp_verified");

        return "redirect:/login?passwordReset=true";
    }

    // ── Helper ────────────────────────────────────────────
    private String maskEmail(String email) {
        if (email == null || !email.contains("@")) return email;
        String[] parts = email.split("@");
        String local = parts[0];
        String domain = parts[1];
        if (local.length() <= 2) return "**@" + domain;
        return local.charAt(0) + "*".repeat(local.length() - 2) + local.charAt(local.length() - 1) + "@" + domain;
    }
}
