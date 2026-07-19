package com.ultracards.server.entity.admin;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "admin_audit_undo_events")
@Getter
@NoArgsConstructor
public class AdminAuditUndoEvent {
    @Id
    @Column(name = "audit_event_id")
    private UUID auditEventId;
    @Column(name = "actor_user_id")
    private Long actorUserId;
    @Column(nullable = false, length = 512)
    private String reason;
    @Column(name = "undone_at", nullable = false)
    private Instant undoneAt;

    public AdminAuditUndoEvent(UUID auditEventId, Long actorUserId, String reason) {
        this.auditEventId = auditEventId;
        this.actorUserId = actorUserId;
        this.reason = reason;
        this.undoneAt = Instant.now();
    }
}
