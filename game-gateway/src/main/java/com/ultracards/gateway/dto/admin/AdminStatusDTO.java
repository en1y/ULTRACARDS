package com.ultracards.gateway.dto.admin;

import java.time.Instant;

public record AdminStatusDTO(
        int apiVersion,
        String serverVersion,
        long uptimeSeconds,
        boolean databaseAvailable,
        String flywayVersion,
        int activeLobbies,
        int activeGames,
        Instant generatedAt
) {
}
