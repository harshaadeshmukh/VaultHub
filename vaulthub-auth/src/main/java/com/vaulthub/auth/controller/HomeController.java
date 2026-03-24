package com.vaulthub.auth.controller;

import com.vaulthub.auth.dto.RegisterRequest;
import com.vaulthub.auth.entity.User;
import com.vaulthub.auth.repository.UserRepository;
import com.vaulthub.auth.service.AuthService;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Slf4j
@Controller
@RequiredArgsConstructor
public class HomeController {

    private final AuthService authService;
    private final UserRepository userRepository;

    // ── 3 second timeout so the dashboard never hangs ──
    private RestTemplate rt() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(4000);
        factory.setReadTimeout(4000);
        return new RestTemplate(factory);
    }

    @GetMapping("/")
    public String home(@AuthenticationPrincipal UserDetails userDetails) {
        if (userDetails != null) return "redirect:/dashboard";
        return "index";
    }

    @GetMapping("/admin-login")
    public String adminLogin(
            @RequestParam(required = false) String error,
            HttpServletRequest request,
            Model model) {
        if (error != null) model.addAttribute("error", "Access denied. Invalid credentials.");
        // Mark this session as coming from admin login page
        request.getSession().setAttribute("loginSource", "admin");
        return "admin-login";
    }

    @GetMapping("/login")
    public String login(
            @RequestParam(required = false) String error,
            @RequestParam(required = false) String registered,
            @RequestParam(required = false) String accountDeleted,
            @RequestParam(required = false) String passwordReset,
            Model model) {
        if (error != null)          model.addAttribute("error", "Invalid email or password!");
        if (registered != null)     model.addAttribute("success", "Account created! Please login.");
        if (accountDeleted != null) model.addAttribute("success", "Your account has been permanently deleted.");
        if (passwordReset != null)  model.addAttribute("success", "Password reset successfully! Please sign in.");
        return "login";
    }

    @GetMapping("/register")
    public String registerPage(Model model) {
        model.addAttribute("registerRequest", new RegisterRequest());
        return "register";
    }

    @PostMapping("/register")
    public String register(
            @Valid @ModelAttribute RegisterRequest request,
            BindingResult bindingResult,
            Model model) {
        if (bindingResult.hasErrors()) return "register";
        try {
            authService.register(request);
            return "redirect:/login?registered=true";
        } catch (RuntimeException e) {
            model.addAttribute("error", e.getMessage());
            return "register";
        }
    }

    @GetMapping("/dashboard")
    public String dashboard(
            @AuthenticationPrincipal UserDetails userDetails,
            jakarta.servlet.http.HttpSession session,
            Model model) {

        User user = authService.findByEmail(userDetails.getUsername());
        model.addAttribute("user", user);
        model.addAttribute("isAdmin", "harshad@gmail.com".equals(user.getEmail()));

        // Seed session with photo so other microservices can render the topbar avatar
        if (user.getProfilePhoto() != null && !user.getProfilePhoto().isBlank()) {
            session.setAttribute("profilePhoto", user.getProfilePhoto());
        }

        long ownerId = (long) userDetails.getUsername().hashCode();

        // Run both HTTP calls to file service IN PARALLEL
        try {
            RestTemplate rt = rt();

            String statsUrl     = "http://localhost:8082/api/files/stats?ownerId=" + ownerId;
            String analyticsUrl = "http://localhost:8082/api/files/analytics?ownerId=" + ownerId;

            java.util.concurrent.CompletableFuture<ResponseEntity<Map<String, Object>>> statsFuture =
                java.util.concurrent.CompletableFuture.supplyAsync(() ->
                    rt.exchange(statsUrl, HttpMethod.GET, null,
                        new ParameterizedTypeReference<Map<String, Object>>() {}));

            java.util.concurrent.CompletableFuture<ResponseEntity<Map<String, Object>>> analyticsFuture =
                java.util.concurrent.CompletableFuture.supplyAsync(() ->
                    rt.exchange(analyticsUrl, HttpMethod.GET, null,
                        new ParameterizedTypeReference<Map<String, Object>>() {}));

            java.util.concurrent.CompletableFuture.allOf(statsFuture, analyticsFuture).join();

            ResponseEntity<Map<String, Object>> statsResp = statsFuture.get();
            if (statsResp.getBody() != null) {
                Map<String, Object> stats = statsResp.getBody();
                model.addAttribute("fileCount",   stats.getOrDefault("fileCount",   0));
                model.addAttribute("totalBytes",  stats.getOrDefault("totalBytes",  0L));
                model.addAttribute("sharedCount", stats.getOrDefault("sharedCount", 0));
            }

            ResponseEntity<Map<String, Object>> analyticsResp = analyticsFuture.get();
            if (analyticsResp.getBody() != null) {
                Map<String, Object> analytics = analyticsResp.getBody();
                model.addAttribute("uploadTrendLabels", analytics.get("uploadTrendLabels"));
                model.addAttribute("uploadTrendData",   analytics.get("uploadTrendData"));
                model.addAttribute("typeLabels",        analytics.get("typeLabels"));
                model.addAttribute("typeData",          analytics.get("typeData"));
                model.addAttribute("topViewed",         analytics.get("topViewed"));
                model.addAttribute("storageUsedMB",     analytics.get("storageUsedMB"));
                model.addAttribute("storageLimitMB",    analytics.get("storageLimitMB"));
                model.addAttribute("storagePercent",    analytics.get("storagePercent"));
            }

        } catch (Exception e) {
            log.warn("Could not fetch file stats: {}", e.getMessage());
            model.addAttribute("fileCount",   0);
            model.addAttribute("totalBytes",  0L);
            model.addAttribute("sharedCount", 0);
            model.addAttribute("uploadTrendLabels", Collections.emptyList());
            model.addAttribute("uploadTrendData",   Collections.emptyList());
            model.addAttribute("typeLabels",        Collections.emptyList());
            model.addAttribute("typeData",          Collections.emptyList());
            model.addAttribute("topViewed",         Collections.emptyList());
            model.addAttribute("storageUsedMB",     0.0);
            model.addAttribute("storageLimitMB",    1024.0);
            model.addAttribute("storagePercent",    0.0);
        }

        return "dashboard";
    }
}
