package org.example.backtrackweb.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Login, logout and role rules.
 *
 * Owners see everything. Employees can work with products, sales and
 * transactions, but account management, settings and reports are owner-only.
 * These rules are enforced on the server; hiding a menu item is only cosmetic.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    /**
     * Clickjacking protection is on by default (the page may not be shown in
     * a frame). It is only turned off for local previews, never for a real
     * deployment - see app.security.allow-frames in application.properties.
     */
    @Value("${app.security.allow-frames:false}")
    private boolean allowFrames;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider(AppUserDetailsService userDetailsService,
                                                            PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return provider;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   DaoAuthenticationProvider authenticationProvider) throws Exception {
        http
                .authenticationProvider(authenticationProvider)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/login", "/css/**", "/js/**", "/images/**", "/icons/**",
                                "/manifest.json", "/sw.js",
                                "/favicon.ico", "/error").permitAll()
                        .requestMatchers("/users/**", "/settings/**", "/reports/**").hasRole("OWNER")
                        .requestMatchers(HttpMethod.POST, "/products/**").hasRole("OWNER")
                        .anyRequest().authenticated())
                .formLogin(form -> form
                        .loginPage("/login")
                        .loginProcessingUrl("/login")
                        .defaultSuccessUrl("/dashboard", true)
                        .failureUrl("/login?error")
                        .permitAll())
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/login?logout")
                        .invalidateHttpSession(true)
                        .deleteCookies("JSESSIONID")
                        .permitAll())
                .headers(headers -> {
                    if (allowFrames) {
                        headers.frameOptions(frame -> frame.disable());
                    }
                })
                .exceptionHandling(handling -> handling.accessDeniedPage("/access-denied"));

        return http.build();
    }
}
