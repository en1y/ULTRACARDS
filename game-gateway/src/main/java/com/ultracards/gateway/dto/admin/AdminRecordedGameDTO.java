package com.ultracards.gateway.dto.admin;

import java.time.Instant;
import java.util.UUID;

public record AdminRecordedGameDTO(
        UUID id,
        String gameType,
        String name,
        Long ownerUserId,
        Instant createdAt,
        Instant startedAt,
        Instant endedAt
) {
}
