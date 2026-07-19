package com.ultracards.gateway.dto.admin;

import java.time.Instant;
import java.util.UUID;

public record AdminSessionDTO(
        UUID id,
        Long userId,
        String clientType,
        String os,
        String country,
        String region,
        Instant firstSeenAt,
        Instant lastSeenAt,
        Instant lastAuthenticatedAt,
        Instant tokenExpiresAt,
        boolean active,
        UUID tokenId
) {
}
