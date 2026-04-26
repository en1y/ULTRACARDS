package com.ultracards.filters;

import com.ultracards.server.entity.auth.TokenEntity;
import com.ultracards.server.service.auth.SessionService;
import com.ultracards.server.service.auth.TokenService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.hibernate.LazyInitializationException;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class TokenRotationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(TokenRotationFilter.class);

    private final TokenService tokenService;
    private final SessionService sessionService;

    @Value("${app.cookie-token.duration-days:15}")
    private int tokenDurationDays;

    @Value("${app.cookie-token.same-site:Lax}")
    private String sameSite;

    @Value("${app.cookie-token.secure:false}")
    private boolean cookieSecure;

    @Value("${app.cookie-token.domain:}")
    private String cookieDomain;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest req,
                                    @NonNull HttpServletResponse res,
                                    @NonNull FilterChain chain) throws IOException, ServletException {
        if (shouldBypass(req)) {
            chain.doFilter(req, res);
            return;
        }

        var token = readRefreshToken(req);

        if (token == null || token.isBlank()) {
            chain.doFilter(req, res);
            return;
        }

        if (!tokenService.tokenExists(token)) {
            handleUnauthorized(req, res);
            return;
        }

        try {
            // 1) rotate token
            var session = sessionService.handleSession(token, req);
            var rotatedToken = session.getToken();

            // 2) reissue cookie
            var cookie = getRefreshToken(rotatedToken);

            res.addHeader("Set-Cookie", cookie.toString());

            // 3) expose token string for controllers that use @RequestAttribute("token")
            req.setAttribute("token", rotatedToken.getToken());

            // 4) authenticate the request so Spring Security stops throwing 401
            var user = rotatedToken.getUser();
            var authorities = user.getAuthorities(); // e.g., Set<UserRole> with UserRole implements GrantedAuthority
            var context = SecurityContextHolder.createEmptyContext();
            var auth = new UsernamePasswordAuthenticationToken(user, null, authorities);
            auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(req));
            context.setAuthentication(auth);
            SecurityContextHolder.setContext(context);

            chain.doFilter(req, res);
        } catch (LazyInitializationException ex) {
            log.error("Lazy init exception. Hibernate is a bitch. {}", ex.getMessage());
        } catch (AccessDeniedException ex) {
            // rotation/validation failed -> unauthenticated
            log.error("Error while validating with token.", ex);
            SecurityContextHolder.clearContext();
            handleUnauthorized(req, res);
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

    private void handleUnauthorized(HttpServletRequest req, HttpServletResponse res) throws IOException {
        if (res.isCommitted()) {
            SecurityContextHolder.clearContext();
            return;
        }

        expireRefreshToken(res);
        SecurityContextHolder.clearContext();

        if (req.getRequestURI().startsWith("/api/")) {
            res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        res.sendRedirect("/errors/401");
    }

    private void expireRefreshToken(HttpServletResponse res) {
        if (res.isCommitted()) {
            return;
        }

        var expiredRefreshToken = ResponseCookie.from("refreshToken", "")
                .path("/")
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite(sameSite)
                .maxAge(0);

        if (cookieDomain != null && !cookieDomain.isBlank()) {
            expiredRefreshToken.domain(cookieDomain);
        }

        res.addHeader("Set-Cookie", expiredRefreshToken.build().toString());
    }

    private boolean shouldBypass(HttpServletRequest req) {
        var path = req.getRequestURI();
        var method = req.getMethod();
        if ("OPTIONS".equals(method)) return true;
        // endpoints where rotation/auth is inappropriate
        return path.startsWith("/active")
                || path.startsWith("/public")
                || path.startsWith("/errors/")
                || path.startsWith("/css/")
                || path.startsWith("/js/")
                || path.startsWith("/pics/")
                || path.equals("/favicon.ico")
                || path.equals("/robots.txt")
                || path.equals("/sitemap.xml");
    }

    private String readRefreshToken(HttpServletRequest req) {
        if (req.getCookies() == null) return null;
        for (var c : req.getCookies()) {
            if ("refreshToken".equals(c.getName())) return c.getValue();
        }
        return null;
    }
}
