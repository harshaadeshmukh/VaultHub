package com.vaulthub.auth.controller;

import com.vaulthub.auth.entity.User;
import com.vaulthub.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserApiController {

    private final UserRepository userRepository;

    // GET /api/users/search?q=email
    @GetMapping("/search")
    public List<Map<String, Object>> searchUsers(
            @RequestParam String q,
            @RequestParam(required = false) String excludeEmail) {
        return userRepository.findAll().stream()
                .filter(u -> u.getRole() != User.Role.ADMIN)
                .filter(u -> u.getEmail().toLowerCase().contains(q.toLowerCase())
                        || u.getFullName().toLowerCase().contains(q.toLowerCase()))
                .filter(u -> excludeEmail == null || !u.getEmail().equalsIgnoreCase(excludeEmail))
                .limit(5)
                .map(u -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("email",    u.getEmail());
                    m.put("fullName", u.getFullName());
                    m.put("vaultId",  u.getVaultId());
                    m.put("initial",  String.valueOf(u.getFullName().charAt(0)).toUpperCase());
                    return m;
                })
                .toList();
    }

    // GET /api/users/by-email?email=...
    @GetMapping("/by-email")
    public Map<String, Object> getUserByEmail(@RequestParam String email) {
        Optional<User> user = userRepository.findByEmail(email);
        if (user.isEmpty() || user.get().getRole() == User.Role.ADMIN) {
            Map<String, Object> m = new HashMap<>();
            m.put("found", false);
            return m;
        }
        User u = user.get();
        Map<String, Object> m = new HashMap<>();
        m.put("found",        true);
        m.put("email",        u.getEmail());
        m.put("fullName",     u.getFullName());
        m.put("vaultId",      u.getVaultId());
        m.put("ownerId",      (long) u.getEmail().hashCode());
        m.put("profilePhoto", u.getProfilePhoto() != null ? u.getProfilePhoto() : "");
        return m;
    }

    // GET /api/users/by-vaultid?vaultId=...
    // Used by chat service to verify a user exists before sending a chat request
    @GetMapping("/by-vaultid")
    public Map<String, Object> getUserByVaultId(@RequestParam String vaultId) {
        Optional<User> user = userRepository.findByVaultId(vaultId);
        if (user.isEmpty() || user.get().getRole() == User.Role.ADMIN) {
            Map<String, Object> m = new HashMap<>();
            m.put("found", false);
            return m;
        }
        User u = user.get();
        Map<String, Object> m = new HashMap<>();
        m.put("found",        true);
        m.put("fullName",     u.getFullName());
        m.put("vaultId",      u.getVaultId());
        m.put("email",        u.getEmail());
        m.put("profilePhoto", u.getProfilePhoto() != null ? u.getProfilePhoto() : "");
        return m;
    }
}
