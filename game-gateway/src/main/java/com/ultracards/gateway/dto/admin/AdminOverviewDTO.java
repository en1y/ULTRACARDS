package com.ultracards.gateway.dto.admin;

import java.time.Instant;
import java.util.Map;

public record AdminOverviewDTO(
        long users,
        Map<String, Long> usersByStatus,
        Map<String, Long> usersByRole,
        long validSessions,
        long onlineUsers,
        long onlineUsersToday,
        Map<String, Long> completedGames,
        Map<String, Long> incompleteGames,
        int activeLobbies,
        int activeGames,
        String flywayVersion,
        Instant generatedAt,
        long totalPoints,
        long pointsChangeLast7Days,
        long pointsMintedLast7Days,
        long pointsRakedLast7Days,
        long pointsEscrowed,
        long dailyClaimsToday
) {
    public AdminOverviewDTO(long users, Map<String, Long> usersByStatus, Map<String, Long> usersByRole,
                            long validSessions, long onlineUsers, long onlineUsersToday,
                            Map<String, Long> completedGames, Map<String, Long> incompleteGames,
                            int activeLobbies, int activeGames, String flywayVersion, Instant generatedAt) {
        this(users, usersByStatus, usersByRole, validSessions, onlineUsers, onlineUsersToday,
                completedGames, incompleteGames, activeLobbies, activeGames, flywayVersion, generatedAt,
                0, 0, 0, 0, 0, 0);
    }
}
