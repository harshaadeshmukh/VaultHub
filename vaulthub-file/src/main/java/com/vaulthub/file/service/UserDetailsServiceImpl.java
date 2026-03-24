package com.vaulthub.file.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
public class UserDetailsServiceImpl implements UserDetailsService {

    @Override
    public UserDetails loadUserByUsername(String email)
            throws UsernameNotFoundException {

        // 🧠 File service has no users table
        //    The session already has the email stored by Auth service
        //    We just need to return a valid UserDetails object
        //    so Spring Security accepts the session as authenticated

        if (email == null || email.isBlank()) {
            throw new UsernameNotFoundException(
                    "No email in session"
            );
        }

        log.debug("🔐 Loading user from session: {}", email);

        // 🧠 Build a minimal UserDetails — no password needed
        //    because we're not doing login here, just reading session
        return User.builder()
                .username(email)
                .password("")          // empty — not used for session auth
                .authorities(List.of(
                        new SimpleGrantedAuthority("ROLE_USER")
                ))
                .accountExpired(false)
                .accountLocked(false)
                .credentialsExpired(false)
                .disabled(false)
                .build();
    }
}