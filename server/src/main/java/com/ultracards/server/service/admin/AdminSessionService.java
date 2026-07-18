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

@Service
@RequiredArgsConstructor
public class AdminSessionService {
    private final SessionRepository sessionRepository;
    private final AdminAuditService auditService;

    @Transactional
    public void expire(UserEntity actor, UUID id, String reason) {
        if (reason == null || reason.isBlank()) throw badRequest("A nonblank reason is required");
        var session = sessionRepository.findById(id).orElseThrow(() -> notFound("Session not found"));
        session.getToken().setActive(false);
        session.getToken().setExpiresAt(Instant.now());
        sessionRepository.save(session);
        auditService.record(actor.getId(), "EXPIRE_SESSION", "SESSION", id.toString(), reason,
                "expired session for user " + session.getUserId(), "SUCCESS");
    }

    @Transactional
    public void delete(UserEntity actor, UUID id, String reason) {
        if (reason == null || reason.isBlank()) throw badRequest("A nonblank reason is required");
        var session = sessionRepository.findById(id).orElseThrow(() -> notFound("Session not found"));
        var userId = session.getUserId();
        sessionRepository.delete(session);
        auditService.record(actor.getId(), "DELETE_SESSION", "SESSION", id.toString(), reason,
                "deleted session for user " + userId, "SUCCESS");
    }

    private ResponseStatusException badRequest(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
    private ResponseStatusException notFound(String message) { return new ResponseStatusException(HttpStatus.NOT_FOUND, message); }
}
