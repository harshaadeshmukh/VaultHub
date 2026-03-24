package com.vaulthub.auth.config;

import com.vaulthub.auth.entity.User;
import com.vaulthub.auth.repository.UserRepository;
import com.vaulthub.auth.service.UserDetailsServiceImpl;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;

import java.io.IOException;
import java.util.Optional;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final UserDetailsServiceImpl userDetailsService;
    private final UserRepository userRepository;
    private static final String ADMIN_EMAIL = "harshad@gmail.com";

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(
                    "/", "/login", "/register", "/admin-login",
                    "/forgot-password", "/forgot-password/**",
                    "/css/**", "/js/**", "/images/**", "/webjars/**", "/api/**"
                ).permitAll()
                // /admin is open to ANY authenticated user here —
                // AdminController itself does the isAdmin check and redirects
                .anyRequest().authenticated()
            )
            .formLogin(form -> form
                .loginPage("/login")
                .loginProcessingUrl("/login")
                .usernameParameter("email")
                .passwordParameter("password")
                .successHandler(customSuccessHandler())
                .failureHandler((req, res, ex) -> {
                    String ref = req.getHeader("Referer");
                    if (ref != null && ref.contains("admin-login")) {
                        res.sendRedirect("/admin-login?error=true");
                    } else {
                        res.sendRedirect("/login?error=true");
                    }
                })
                .permitAll()
            )
            .logout(logout -> logout
                .logoutUrl("/logout")
                .logoutSuccessUrl("/")
                .invalidateHttpSession(true)
                .deleteCookies("SESSION")
                .permitAll()
            )
            .csrf(csrf -> csrf.disable());

        return http.build();
    }

    @Bean
    public AuthenticationSuccessHandler customSuccessHandler() {
        return (request, response, authentication) -> {
            String email = authentication.getName();
            Optional<User> userOpt = userRepository.findByEmail(email);

            if (userOpt.isPresent()) {
                HttpSession session = request.getSession();
                session.setAttribute("fullName", userOpt.get().getFullName());
                session.setAttribute("vaultId",  userOpt.get().getVaultId());
                session.setAttribute("email",    userOpt.get().getEmail());
                session.setAttribute("isAdmin",  ADMIN_EMAIL.equals(email));
            }

            boolean isAdmin = ADMIN_EMAIL.equals(email);
            String dest = isAdmin ? "/admin" : "/dashboard";

            String proto = request.getHeader("X-Forwarded-Proto");
            String host  = request.getHeader("X-Forwarded-Host");
            String port  = request.getHeader("X-Forwarded-Port");

            if (proto != null && host != null) {
                String base = proto + "://" + host;
                if (port != null && !port.equals("80") && !port.equals("443")) {
                    base += ":" + port;
                }
                response.sendRedirect(base + dest);
            } else {
                response.sendRedirect(dest);
            }
        };
    }

    @Bean
    public DaoAuthenticationProvider authProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
