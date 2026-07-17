package com.ultracards.gateway.dto.admin;

import java.time.Instant;
import java.util.Map;

public record AdminOverviewDTO(
        long users,
        Map<String, Long> usersByStatus,
        Map<String, Long> usersByRole,
        long validSessions,
        long onlineUsers,
        Map<String, Long> completedGames,
        Map<String, Long> incompleteGames,
        int activeLobbies,
        int activeGames,
        String flywayVersion,
        Instant generatedAt
) {
}
