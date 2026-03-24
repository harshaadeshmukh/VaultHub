package com.vaulthub.auth.service;

import com.vaulthub.auth.entity.User;
import com.vaulthub.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;

import java.util.Collections;

// ════════════════════════════════════════════════════════
//  AUTH SERVICE — UserDetailsServiceImpl
//
//  PURPOSE: Load user from MySQL during LOGIN
//           Spring Security calls loadUserByUsername()
//           to verify email + password
//
//  This file is ONLY in decentravault-auth
//  The file service has its own simpler version
// ════════════════════════════════════════════════════════

@Slf4j
@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserRepository userRepository;

    // ── Called by Spring Security during login ──
    // Spring asks: "give me user with this email"
    // We load from DB and return UserDetails
    // Spring then checks password hash automatically
    @Override
    public UserDetails loadUserByUsername(String email)
            throws UsernameNotFoundException {

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> {
                    log.warn("❌ Login failed — user not found: {}", email);
                    return new UsernameNotFoundException(
                            "User not found: " + email
                    );
                });

        log.info("✅ User loaded: {}", email);

        return new org.springframework.security.core.userdetails.User(
                user.getEmail(),
                user.getPassword(),
                Collections.singletonList(
                        new SimpleGrantedAuthority("ROLE_" + user.getRole().name())
                )
        );
    }

    // ── Called by SecurityConfig after login success ──
    // We use this to get the full User entity (with fullName)
    // so we can store it in the Redis session
    // File service then reads fullName from session to show in topbar
    public User loadFullUser(String email) {
        return userRepository.findByEmail(email).orElse(null);
    }
}