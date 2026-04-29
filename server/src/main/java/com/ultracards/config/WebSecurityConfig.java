package com.ultracards.config;

import com.ultracards.filters.TokenRotationFilter;
import jakarta.servlet.ServletException;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.io.IOException;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true,  securedEnabled = true, jsr250Enabled = true)
@RequiredArgsConstructor
public class WebSecurityConfig {

    private final TokenRotationFilter tokenRotationFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(AbstractHttpConfigurer::disable)
                .anonymous(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(tokenRotationFilter, UsernamePasswordAuthenticationFilter.class)
                .authorizeHttpRequests(auth -> auth
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        // Public static/pages
                        .requestMatchers(
                                "/",
                                "/error",
                                "/errors/**",
                                "/favicon.ico",
                                "/css/**",
                                "/pics/**",
                                "/js/**"
                        ).permitAll()
                        // Public app routes
                        .requestMatchers(
                                "/active",
                                "/ws",
                                "/ws/**"
                        ).permitAll()
                        // Public auth endpoints
                        .requestMatchers(HttpMethod.POST,
                                "/api/auth/email/send",
                                "/api/auth/email/verify",
                                "/api/auth/logout"
                        ).permitAll()
                        // Post auth available endpoints
                        .requestMatchers(
                                "/profile/**",
                                "/lobbies/**",
                                "/game/**"
                        ).authenticated()
                        // backend API
                        // TODO: check whether i should to make any more of the api calls public
                        .requestMatchers("/api/**").authenticated()
                        // Everything else public
                        .anyRequest().permitAll()
                )
                .exceptionHandling(h -> h
                        .authenticationEntryPoint(authenticationEntryPoint())
                        .accessDeniedHandler(accessDeniedHandler())
                );

        return http.build();
    }

    private AuthenticationEntryPoint authenticationEntryPoint() {
        return (req, res, ex) -> handleAuthFailure(req, res, HttpServletResponse.SC_UNAUTHORIZED);
    }

    private AccessDeniedHandler accessDeniedHandler() {
        return (req, res, ex) -> handleForbidden(req, res);
    }

    private void handleAuthFailure(HttpServletRequest req, HttpServletResponse res, int status)
            throws ServletException, IOException {
        if (req.getRequestURI().startsWith("/api/")) {
            res.setStatus(status);
            return;
        }

        res.sendError(status);
    }

    private void handleForbidden(HttpServletRequest req, HttpServletResponse res) throws IOException {
        if (req.getRequestURI().startsWith("/api/")) {
            res.setStatus(HttpServletResponse.SC_FORBIDDEN);
            return;
        }

        res.sendError(HttpServletResponse.SC_FORBIDDEN);
    }
}
