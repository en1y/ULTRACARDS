package com.ultracards.server.filters;

import com.ultracards.server.entity.auth.TokenEntity;
import com.ultracards.server.service.auth.TokenService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.file.AccessDeniedException;

@Component
public class TokenRotationFilter extends OncePerRequestFilter {
    @Value("${app.cookie-token.duration-days:15}")
    private int tokenDurationDays;

    private final TokenService tokenService;

    public TokenRotationFilter(TokenService tokenService) {
        this.tokenService = tokenService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req,
                                    HttpServletResponse res,
                                    FilterChain chain) throws IOException, ServletException {
        String path = req.getRequestURI();
        // Skip paths that shouldn't rotate
        if (path.startsWith("/api/auth/logout") || path.startsWith("/public") || path.startsWith("/active") || path.startsWith("/api/auth/email/")) {
            chain.doFilter(req, res);
            return;
        }

        String token = readRefreshToken(req);

        if (token == null || token.isBlank()) {
            chain.doFilter(req, res);
            return;
        }

        try {
            var rotatedToken = tokenService.rotateToken(token);

            // make it available to controllers/services
            req.setAttribute("tokenEntity", rotatedToken.getToken());

            // set rotatedToken cookie
            var cookie = getCookie(rotatedToken);
            res.addHeader("Set-Cookie", cookie.toString());

            chain.doFilter(req, res);
        } catch (AccessDeniedException ex) {
            res.setStatus(HttpServletResponse.SC_FOUND);
            res.setHeader("Location", "/api/auth/logout");
        }
    }

    private ResponseCookie getCookie(TokenEntity rotated) {
        return ResponseCookie.from("refreshToken", rotated.getToken())
                .path("/")
                .maxAge(24L * 3600 * tokenDurationDays)
                .httpOnly(true)
                .sameSite("Strict")
                .build();
    }

    private String readRefreshToken(HttpServletRequest req) {
        if (req.getCookies() == null) return null;
        for (var c : req.getCookies()) {
            if ("refreshToken".equals(c.getName())) return c.getValue();
        }
        return null;
    }
}

