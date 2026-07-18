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
@Table(name = "admin_audit_events")
@Getter
@NoArgsConstructor
public class AdminAuditEvent {
    @Id
    private UUID id;
    @Column(name = "actor_user_id")
    private Long actorUserId;
    @Column(nullable = false, length = 64)
    private String action;
    @Column(name = "target_type", nullable = false, length = 64)
    private String targetType;
    @Column(name = "target_id", nullable = false, length = 128)
    private String targetId;
    @Column(length = 512)
    private String reason;
    @Column(nullable = false, length = 1024)
    private String summary;
    @Column(nullable = false, length = 32)
    private String outcome;
    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    public AdminAuditEvent(Long actorUserId, String action, String targetType, String targetId,
                           String reason, String summary, String outcome) {
        this.id = UUID.randomUUID();
        this.actorUserId = actorUserId;
        this.action = action;
        this.targetType = targetType;
        this.targetId = targetId;
        this.reason = reason;
        this.summary = summary;
        this.outcome = outcome;
        this.occurredAt = Instant.now();
    }
}
