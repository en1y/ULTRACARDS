package com.ultracards.server.service.admin;

import com.ultracards.server.entity.UserEntity;
import com.ultracards.server.repositories.auth.SessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.UUID;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AdminSessionService {
    private final SessionRepository sessionRepository;
    private final AdminAuditService auditService;

    @Transactional
    public void expire(UserEntity actor, UUID id, String reason) {
        if (reason == null || reason.isBlank()) throw badRequest("A nonblank reason is required");
        var session = sessionRepository.findById(id).orElseThrow(() -> notFound("Session not found"));
        var previousActive = session.getToken().isActive();
        var previousExpiresAt = session.getToken().getExpiresAt();
        session.getToken().setActive(false);
        session.getToken().setExpiresAt(Instant.now());
        sessionRepository.save(session);
        auditService.record(actor.getId(), "EXPIRE_SESSION", "SESSION", id.toString(), reason,
                "expired session for user " + session.getUserId(), "SUCCESS",
                Map.of("active", previousActive, "expiresAt", previousExpiresAt));
    }

    @Transactional
    public void delete(UserEntity actor, UUID id, String reason) {
        if (reason == null || reason.isBlank()) throw badRequest("A nonblank reason is required");
        var session = sessionRepository.findById(id).orElseThrow(() -> notFound("Session not found"));
        var userId = session.getUserId();
        // The token secret is deliberately excluded: an undone session comes back expired.
        var payload = new java.util.HashMap<String, Object>();
        payload.put("userId", session.getUserId());
        payload.put("deviceId", session.getDeviceId());
        payload.put("clientType", session.getClientType());
        payload.put("os", session.getOs());
        payload.put("ipHash", session.getIpHash());
        payload.put("country", session.getCountry());
        payload.put("region", session.getRegion());
        payload.put("userAgent", session.getUserAgent());
        payload.put("firstSeenAt", session.getFirstSeenAt());
        payload.put("lastSeenAt", session.getLastSeenAt());
        payload.put("lastAuthenticatedAt", session.getLastAuthenticatedAt());
        payload.put("tokenExpiresAt", session.getToken().getExpiresAt());
        sessionRepository.delete(session);
        auditService.record(actor.getId(), "DELETE_SESSION", "SESSION", id.toString(), reason,
                "deleted session for user " + userId, "SUCCESS", payload);
    }

    private ResponseStatusException badRequest(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
    private ResponseStatusException notFound(String message) { return new ResponseStatusException(HttpStatus.NOT_FOUND, message); }
}
