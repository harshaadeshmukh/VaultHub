package com.vaulthub.auth.config;

import com.vaulthub.auth.entity.User;
import com.vaulthub.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AdminSeeder implements ApplicationRunner {

    private static final String ADMIN_EMAIL    = "harshad@gmail.com";
    private static final String ADMIN_PASSWORD = "1234";
    private static final String ADMIN_NAME     = "Harshad";
    private static final String ADMIN_VAULT_ID = "vault-admin-0001";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(ApplicationArguments args) {
        userRepository.findByEmail(ADMIN_EMAIL).ifPresentOrElse(
            user -> {
                // Always force-reset password + role to guarantee login works
                user.setRole(User.Role.ADMIN);
                user.setPassword(passwordEncoder.encode(ADMIN_PASSWORD));
                userRepository.save(user);
                log.info("🔑 Admin account synced: {} / {}", ADMIN_EMAIL, ADMIN_PASSWORD);
            },
            () -> {
                User admin = User.builder()
                        .fullName(ADMIN_NAME)
                        .email(ADMIN_EMAIL)
                        .password(passwordEncoder.encode(ADMIN_PASSWORD))
                        .vaultId(ADMIN_VAULT_ID)
                        .role(User.Role.ADMIN)
                        .build();
                userRepository.save(admin);
                log.info("🚀 Admin account created: {} / {}", ADMIN_EMAIL, ADMIN_PASSWORD);
            }
        );
    }
}
