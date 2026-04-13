package com.ultracards.server.service.auth;

import com.ultracards.server.entity.UserEntity;
import com.ultracards.server.entity.auth.TokenEntity;
import com.ultracards.server.entity.auth.UserSession;
import com.ultracards.server.repositories.auth.SessionRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class SessionService {
    private final SessionRepository sessionRepository;
    private final TokenService tokenService;

    @Transactional
    public UserSession createSession(UserEntity user, HttpServletRequest request) {
        var session = new UserSession();
        var token = tokenService.createToken(user);
        session.setUserId(token.getUser().getId());
        session.setToken(token);

        var now = Instant.now();
        session.setFirstSeenAt(now);
        session.setLastSeenAt(now);
        session.setLastAuthenticatedAt(now);
        session.setCurrentSession(true);

        updateSessionByRequest(session, request);

        session = sessionRepository.save(session);
        return session;
    }

    @Transactional
    public UserSession handleSession(String tokenString, HttpServletRequest request) {
        var currentToken = tokenService.getToken(tokenString);
        var session = sessionRepository.findByToken(currentToken)
                .orElseThrow(() -> new AccessDeniedException("Session not found"));
        var token = tokenService.rotateToken(tokenString);
        session.setToken(token);
        session.setLastSeenAt(Instant.now());
        updateSessionByRequest(session, request);
        session = sessionRepository.save(session);
        session.setCurrentSession(true);
        return session;
    }

    private void updateSessionByRequest(UserSession session, HttpServletRequest request) {
        session.setDeviceId(request.getHeader("X-Client-Device-Id"));
        session.setClientType(getClientType(request));
        session.setOs(getOs(request));
        session.setUserAgent(request.getHeader("User-Agent"));
        // TODO: make it into a hash
        session.setIpHash(getClientIp(request));
        session.setCountry(request.getHeader("X-Client-Country"));
        session.setRegion(request.getHeader("X-Client-Region"));
    }

    private String getOs(HttpServletRequest request) {
        var explicit = getHeaderOrNull(request, "X-Client-OS");
        if (explicit != null) {
            return explicit;
        }

        var userAgent = getHeaderOrNull(request, "User-Agent");
        if (userAgent == null) {
            return "unknown";
        }

        var ua = userAgent.toLowerCase();
        if (ua.contains("windows")) return "Windows";
        if (ua.contains("mac os")) return "macOS";
        if (ua.contains("android")) return "Android";
        if (ua.contains("iphone") || ua.contains("ipad") || ua.contains("ios")) return "iOS";
        if (ua.contains("linux")) return "Linux";

        return "unknown";
    }

    private String getClientIp(HttpServletRequest request) {
        String ips = request.getHeader("X-Forwarded-For");
        if (ips != null && !ips.isBlank()) {
            return ips.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private String getClientType(HttpServletRequest request) {
        var explicit = getHeaderOrNull(request, "X-Client-Type");
        if (explicit != null) return explicit;
        var userAgent = getHeaderOrNull(request, "User-Agent");
        if (userAgent == null) {
            return "unknown";
        }
        if (userAgent.contains("mobile") || userAgent.contains("android") || userAgent.contains("iphone"))
            return "mobile";
        if (userAgent.contains("app"))
            return "app";
        return "browser";
    }

    private String getHeaderOrNull(HttpServletRequest request, String headerName) {
        var header = request.getHeader(headerName);
        if (header == null) return null;
        header = header.trim();
        return header.isEmpty() ? null : header;
    }
}
