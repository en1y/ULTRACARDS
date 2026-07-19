package com.ultracards.gateway.dto.admin;

import java.time.Instant;
import java.util.UUID;

/** Token metadata only; the secret token value is never exposed. */
public record AdminTokenDTO(
        UUID id,
        Long userId,
        UUID sessionId,
        boolean active,
        boolean valid,
        Instant expiresAt,
        Instant reuseUntil,
        UUID rotatedToTokenId
) {
}
