package com.ultracards.filters;

import com.ultracards.server.entity.auth.TokenEntity;
import com.ultracards.server.service.auth.TokenService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.hibernate.LazyInitializationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.file.AccessDeniedException;

@Component
@RequiredArgsConstructor
public class TokenRotationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(TokenRotationFilter.class);

    private final TokenService tokenService;

    @Value("${app.cookie-token.duration-days:15}")
    private int tokenDurationDays;

    @Value("${app.cookie-token.same-site:Lax}")
    private String sameSite;

    @Value("${app.cookie-token.secure:false}")
    private boolean cookieSecure;

    @Value("${app.cookie-token.domain:}")
    private String cookieDomain;

    @Override
    protected void doFilterInternal(HttpServletRequest req,
                                    HttpServletResponse res,
                                    FilterChain chain) throws IOException, ServletException {

        if (shouldBypass(req)) {
            chain.doFilter(req, res);
            return;
        }

        var token = readRefreshToken(req);
        if (token == null || token.isBlank()) {
            chain.doFilter(req, res);
            return;
        }

        try {
            // 1) rotate token
            var rotatedToken = tokenService.rotateToken(token);

            // 2) reissue cookie
            var cookie = getRefreshToken(rotatedToken);

            res.addHeader("Set-Cookie", cookie.toString());

            // 3) expose token string for controllers that use @RequestAttribute("tokenEntity")
            req.setAttribute("refreshToken", rotatedToken.getToken());

            // 4) authenticate the request so Spring Security stops throwing 401
            var user = rotatedToken.getUser();
            var authorities = user.getAuthorities(); // e.g., Set<UserRole> with UserRole implements GrantedAuthority
            var auth = new UsernamePasswordAuthenticationToken(user, null, authorities);
            auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(req));
            var context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(auth);
            SecurityContextHolder.setContext(context);

            chain.doFilter(req, res);
        } catch (LazyInitializationException ex) {
            log.error("Lazy init exception. Hibernate is a bitch. {}", ex.getMessage());
        } catch (AccessDeniedException ex) {
            // rotation/validation failed -> unauthenticated
            log.error("Error while validating with token.", ex);
            SecurityContextHolder.clearContext();
            res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        }
    }

    private ResponseCookie getRefreshToken(TokenEntity rotated) {
        var cookie = ResponseCookie.from("refreshToken", rotated.getToken())
                .path("/")
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite(sameSite)
                .maxAge(24L * 3600 * tokenDurationDays);

        if (cookieDomain != null && !cookieDomain.isBlank()) cookie.domain(cookieDomain);

        return cookie.build();
    }

    private boolean shouldBypass(HttpServletRequest req) {
        var path = req.getRequestURI();
        var method = req.getMethod();
        if ("OPTIONS".equals(method)) return true;
        // endpoints where rotation/auth is inappropriate
        return path.startsWith("/active")
                || path.startsWith("/public")
                || path.startsWith("/api/auth/logout")
                || path.startsWith("/api/auth/email/");
    }

    private String readRefreshToken(HttpServletRequest req) {
        if (req.getCookies() == null) return null;
        for (var c : req.getCookies()) {
            if ("refreshToken".equals(c.getName())) return c.getValue();
        }
        return null;
    }
}

