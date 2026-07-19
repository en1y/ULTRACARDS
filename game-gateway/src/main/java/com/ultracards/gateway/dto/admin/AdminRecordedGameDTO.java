package com.ultracards.gateway.dto.admin;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AdminRecordedGameDTO(
        UUID id,
        String gameType,
        String mode,
        String name,
        Long ownerUserId,
        Instant createdAt,
        Instant startedAt,
        Instant endedAt,
        List<String> players,
        List<String> winners,
        int rounds
) {
}
