package com.vaulthub.auth.service;

import com.vaulthub.auth.dto.RegisterRequest;
import com.vaulthub.auth.entity.User;
import com.vaulthub.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    // ══════════════════════════════════════════
    //   REGISTER
    // ══════════════════════════════════════════

    @Transactional
    public User register(RegisterRequest request) {

        // Step 1 — Validate
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException(
                    "Email already registered!"
            );
        }

        if (!request.getPassword()
                .equals(request.getConfirmPassword())) {
            throw new RuntimeException(
                    "Passwords do not match!"
            );
        }

        log.info("📝 Registering new user: {}", request.getEmail());

        // Step 2 — Hash password
        // 🧠 BCrypt: "mypassword" → "$2a$10$xyz..."
        String hashedPassword = passwordEncoder
                .encode(request.getPassword());

        // Step 3 — Generate unique vault ID
        // 🧠 UUID = universally unique identifier
        //    Example: vault-550e8400e29b
        String vaultId = "vault-" + UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 12);

        // Step 4 — Build and save user
        User user = User.builder()
                .fullName(request.getFullName())
                .email(request.getEmail())
                .password(hashedPassword)
                .vaultId(vaultId)
                .role(User.Role.USER)
                .build();

        User savedUser = userRepository.save(user);
        log.info("✅ User registered: {} | VaultID: {}",
                savedUser.getEmail(), savedUser.getVaultId());

        return savedUser;
    }

    // ══════════════════════════════════════════
    //   FIND USER
    // ══════════════════════════════════════════

    public User findByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() ->
                        new RuntimeException("User not found: " + email));
    }

    public boolean emailExists(String email) {
        return userRepository.existsByEmail(email);
    }

    // ── Save (update) an existing user ──
    public User saveUser(User user) {
        return userRepository.save(user);
    }
}
