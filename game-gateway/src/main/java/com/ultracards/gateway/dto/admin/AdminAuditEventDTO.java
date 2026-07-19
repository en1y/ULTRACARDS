package com.ultracards.gateway.dto.admin;

import java.time.Instant;
import java.util.UUID;

public record AdminAuditEventDTO(
        UUID id,
        Long actorUserId,
        String action,
        String targetType,
        String targetId,
        String reason,
        String summary,
        String outcome,
        Instant occurredAt,
        boolean undoable,
        boolean undone
) {
}
