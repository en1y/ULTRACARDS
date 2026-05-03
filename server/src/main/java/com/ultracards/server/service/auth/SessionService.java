package com.ultracards.server.service.auth;

import com.ultracards.gateway.dto.auth.UserSessionDTO;
import com.ultracards.server.entity.UserEntity;
import com.ultracards.server.entity.auth.UserSession;
import com.ultracards.server.repositories.auth.SessionRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SessionService {
    private final SessionRepository sessionRepository;
    private final TokenService tokenService;

    @Value("${app.token.update-privilege-duration-minutes:4}")
    private long updateDuration;

    @Transactional
    public UserSession getSession(UUID id) {
        return sessionRepository.findById(id)
                .orElseThrow(() -> new AccessDeniedException("Session not found"));
    }

    @Transactional
    public UserSession getSession(String token) {
        var tokenEntity = tokenService.getToken(token);
        var session = sessionRepository.findByToken(tokenEntity)
                .orElseThrow(() -> new AccessDeniedException("Session not found"));
        session.setLastSeenAt(Instant.now());
        return sessionRepository.save(session);
    }

    @Transactional
    public UserSession recreateTokenForSession(String token) {
        var session = getSession(token);
        var oldToken = session.getToken();
        var newToken = tokenService.createToken(session.getToken().getUser());
        session.setToken(newToken);
        session.setLastAuthenticatedAt(Instant.now());
        session = sessionRepository.saveAndFlush(session);
        tokenService.deleteToken(oldToken);
        return session;
    }

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
        session = sessionRepository.saveAndFlush(session);
        if (!currentToken.getId().equals(token.getId())) {
            tokenService.deleteToken(currentToken);
        }
        session.setCurrentSession(true);
        return session;
    }

    @Transactional
    public List<UserSessionDTO> getSessions(UserEntity user, String token) {
        var sessions = sessionRepository.findAllByUserId(user.getId());
        var res = new ArrayList<UserSessionDTO>();
        for (var session : sessions) {
            var dto = new UserSessionDTO();
            dto.setId(session.getId());
            dto.setFirstSeenAt(session.getFirstSeenAt());
            dto.setLastSeenAt(session.getLastSeenAt());
            dto.setLastAuthenticatedAt(session.getLastAuthenticatedAt());
            dto.setDeviceId(session.getDeviceId());
            dto.setClientType(session.getClientType());
            dto.setOs(session.getOs());
            dto.setUserAgent(session.getUserAgent());
            dto.setCountry(session.getCountry());
            dto.setRegion(session.getRegion());
            dto.setCurrentSession(session.getToken().getToken().equals(token));
            res.add(dto);
        }
        return res;
    }

    public Boolean deleteSession(UserSession userSession, UserSession deleteSession) {
        var authenticatedAt = userSession.getLastAuthenticatedAt();
        if (Instant.now().isAfter(authenticatedAt.plusSeconds(updateDuration * 60)))
            return false;
        sessionRepository.delete(deleteSession);
        return true;
    }

    @Transactional
    public void logout(UserSession session) {
        sessionRepository.delete(session);
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
