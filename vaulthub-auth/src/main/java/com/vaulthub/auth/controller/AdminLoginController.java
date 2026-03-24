package com.vaulthub.auth.controller;

import com.vaulthub.auth.entity.User;
import com.vaulthub.auth.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Optional;

@Slf4j
@Controller
@RequiredArgsConstructor
public class AdminLoginController {

    private static final String ADMIN_EMAIL = "harshad@gmail.com";

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;

    @PostMapping("/admin-login-process")
    public String process(
            @RequestParam String email,
            @RequestParam String password,
            HttpServletRequest request) {

        // Only the hardcoded admin email is allowed
        if (!ADMIN_EMAIL.equalsIgnoreCase(email.trim())) {
            log.warn("Admin login attempt with non-admin email: {}", email);
            return "redirect:/admin-login?error=true";
        }

        try {
            // Let Spring Security verify the password against the DB hash
            Authentication auth = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(email.trim(), password)
            );

            // Store authentication in SecurityContext and session
            SecurityContext sc = SecurityContextHolder.createEmptyContext();
            sc.setAuthentication(auth);
            SecurityContextHolder.setContext(sc);

            HttpSession session = request.getSession(true);
            session.setAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, sc
            );

            // Store display attributes
            Optional<User> userOpt = userRepository.findByEmail(email.trim());
            userOpt.ifPresent(u -> {
                session.setAttribute("fullName", u.getFullName());
                session.setAttribute("vaultId",  u.getVaultId());
                session.setAttribute("isAdmin",  true);
            });

            log.info("✅ Admin logged in: {}", email);
            return "redirect:/admin";

        } catch (BadCredentialsException e) {
            log.warn("❌ Admin login failed — bad password for: {}", email);
            return "redirect:/admin-login?error=true";
        } catch (Exception e) {
            log.error("❌ Admin login error: {}", e.getMessage());
            return "redirect:/admin-login?error=true";
        }
    }
}
