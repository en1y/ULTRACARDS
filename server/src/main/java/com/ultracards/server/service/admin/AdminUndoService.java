package com.ultracards.server.service.admin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ultracards.server.entity.UserEntity;
import com.ultracards.server.entity.admin.AdminAuditUndoEvent;
import com.ultracards.server.entity.notifications.NotificationEntity;
import com.ultracards.server.enums.NotificationType;
import com.ultracards.server.repositories.UserRepository;
import com.ultracards.server.repositories.admin.AdminAuditEventRepository;
import com.ultracards.server.repositories.admin.AdminAuditUndoEventRepository;
import com.ultracards.server.repositories.notifications.NotificationRepository;
import com.ultracards.server.entity.auth.TokenEntity;
import com.ultracards.server.entity.auth.UserSession;
import com.ultracards.server.repositories.auth.SessionRepository;
import com.ultracards.server.repositories.auth.TokenRepository;
import com.ultracards.server.repositories.games.RecordedGameRepository;
import com.ultracards.server.enums.UserRole;
import com.ultracards.server.enums.UserStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdminUndoService {
    private final AdminAuditEventRepository auditRepository;
    private final AdminAuditUndoEventRepository undoRepository;
    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final SessionRepository sessionRepository;
    private final TokenRepository tokenRepository;
    private final RecordedGameRepository recordedGameRepository;
    private final ObjectMapper objectMapper;
    // persist (not save/merge) keeps assigned ids when recreating deleted rows
    @jakarta.persistence.PersistenceContext
    private jakarta.persistence.EntityManager entityManager;

    @Transactional
    public void undo(UserEntity actor, UUID auditId, String reason) {
        if (reason == null || reason.isBlank()) throw badRequest("A nonblank reason is required");
        var event = auditRepository.findById(auditId).orElseThrow(() -> notFound("Audit event not found"));
        if (event.getUndoPayload() == null) throw badRequest("This audit action cannot be undone");
        if (undoRepository.existsById(auditId)) throw badRequest("This audit action was already undone");
        if (Duration.between(event.getOccurredAt(), Instant.now()).toHours() >= 24)
            throw badRequest("Undo is available for 24 hours after the action");
        try {
            var state = objectMapper.readTree(event.getUndoPayload());
            switch (event.getAction()) {
                case "UPDATE_NOTIFICATION" -> restoreNotification(state);
                case "DELETE_NOTIFICATION" -> recreateNotification(state);
                case "UPDATE_USER" -> restoreUser(event.getTargetId(), state);
                case "GRANT_ROLE", "REVOKE_ROLE" -> restoreRole(event.getAction(), event.getTargetId(), state);
                case "UPDATE_GAME_RECORD" -> restoreGame(event.getTargetId(), state);
                case "EXPIRE_SESSION" -> restoreSession(event.getTargetId(), state);
                case "DELETE_SESSION" -> recreateSession(event.getTargetId(), state);
                default -> throw badRequest("This audit action cannot be undone yet");
            }
            undoRepository.save(new AdminAuditUndoEvent(auditId, actor.getId(), reason.trim()));
        } catch (ResponseStatusException ex) { throw ex; }
        catch (Exception ex) { throw new IllegalStateException("Could not undo audit action", ex); }
    }

    private void restoreNotification(JsonNode state) {
        var notification = notificationRepository.findById(UUID.fromString(state.get("id").asText()))
                .orElseThrow(() -> notFound("Notification no longer exists"));
        notification.setMessage(state.get("message").isNull() ? null : state.get("message").asText());
        if (state.get("read").asBoolean()) notification.markRead(); else notification.markUnread();
    }

    private void recreateNotification(JsonNode state) {
        var notification = new NotificationEntity(user(state.get("recipientId")), user(state.get("senderId")),
                NotificationType.valueOf(state.get("type").asText()),
                state.get("message").isNull() ? null : state.get("message").asText(),
                uuid(state, "lobbyId"), uuid(state, "friendRequestId"));
        notification.setRead(state.get("read").asBoolean());
        notification.setCreatedAt(Instant.parse(state.get("createdAt").asText()));
        if (!notification.isRead()) notification.setReadAt(null);
        else notification.setReadAt(Instant.parse(state.get("readAt").asText()));
        entityManager.persist(notification);
    }

    private void restoreUser(String targetId, JsonNode state) {
        var user = userRepository.findById(Long.valueOf(targetId)).orElseThrow(() -> notFound("User no longer exists"));
        user.setEmail(state.get("email").asText()); user.setUsername(state.get("username").asText());
        user.setEnabled(state.get("enabled").asBoolean()); user.setStatus(UserStatus.valueOf(state.get("status").asText()));
        user.setFakeAdmin(state.path("fakeAdmin").asBoolean(false));
        var roles = new java.util.HashSet<UserRole>();
        state.get("roles").forEach(role -> roles.add(UserRole.valueOf(role.asText())));
        user.setRoles(roles); userRepository.save(user);
    }

    private void restoreRole(String action, String targetId, JsonNode state) {
        var user = userRepository.findById(Long.valueOf(targetId)).orElseThrow(() -> notFound("User no longer exists"));
        var role = UserRole.valueOf(state.get("role").asText());
        if (state.get("wasPresent").asBoolean()) user.addRole(role); else user.removeRole(role);
        userRepository.save(user);
    }

    private void restoreGame(String targetId, JsonNode state) {
        var game = recordedGameRepository.findById(UUID.fromString(targetId)).orElseThrow(() -> notFound("Recorded game no longer exists"));
        game.rename(state.get("name").asText()); recordedGameRepository.save(game);
    }

    private void recreateSession(String targetId, JsonNode state) {
        if (sessionRepository.existsById(UUID.fromString(targetId))) throw badRequest("Session already exists");
        var token = new TokenEntity();
        // The original token secret was never persisted, so the restored session is unusable for sign-in.
        token.setToken("undone-" + UUID.randomUUID());
        token.setActive(false);
        token.setExpiresAt(Instant.parse(state.get("tokenExpiresAt").asText()));
        token.setUser(userRepository.getReferenceById(state.get("userId").asLong()));
        entityManager.persist(token);
        var session = new UserSession(state.get("userId").asLong(), token, text(state, "deviceId"), text(state, "clientType"),
                text(state, "os"), state.get("ipHash").asText(), text(state, "country"), text(state, "region"), text(state, "userAgent"));
        // ponytail: generated ids only — persist rejects assigned ids on @GeneratedValue entities, so the restored row gets a fresh id
        session.setFirstSeenAt(Instant.parse(state.get("firstSeenAt").asText()));
        session.setLastSeenAt(Instant.parse(state.get("lastSeenAt").asText()));
        session.setLastAuthenticatedAt(Instant.parse(state.get("lastAuthenticatedAt").asText()));
        entityManager.persist(session);
    }

    private String text(JsonNode state, String name) { return state.get(name) == null || state.get(name).isNull() ? null : state.get(name).asText(); }

    private void restoreSession(String targetId, JsonNode state) {
        var session = sessionRepository.findById(UUID.fromString(targetId)).orElseThrow(() -> notFound("Session no longer exists"));
        session.getToken().setActive(state.get("active").asBoolean());
        session.getToken().setExpiresAt(Instant.parse(state.get("expiresAt").asText()));
        sessionRepository.save(session);
    }

    private UserEntity user(JsonNode id) { return userRepository.getReferenceById(id.asLong()); }
    private UUID uuid(JsonNode state, String name) { return state.get(name).isNull() ? null : UUID.fromString(state.get(name).asText()); }
    private ResponseStatusException badRequest(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
    private ResponseStatusException notFound(String message) { return new ResponseStatusException(HttpStatus.NOT_FOUND, message); }
}
