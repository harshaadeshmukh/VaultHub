package com.vaulthub.chat.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

// ════════════════════════════════════════════════════════
//  Exact same pattern as vaulthub-file UserDetailsServiceImpl.
//  The file service works — so we copy it exactly.
//
//  The Redis session already has the email stored by the
//  auth service. Spring Security calls loadUserByUsername(email)
//  to rebuild UserDetails from the session. We just return
//  a valid object — no password check needed here.
// ════════════════════════════════════════════════════════
@Slf4j
@Service
public class UserDetailsServiceImpl implements UserDetailsService {

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        if (email == null || email.isBlank()) {
            throw new UsernameNotFoundException("No email in session");
        }
        log.debug("Loading chat user from session: {}", email);
        return User.builder()
                .username(email)
                .password("")
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_USER")))
                .accountExpired(false)
                .accountLocked(false)
                .credentialsExpired(false)
                .disabled(false)
                .build();
    }
}
