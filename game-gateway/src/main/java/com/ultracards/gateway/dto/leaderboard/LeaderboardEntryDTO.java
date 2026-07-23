package com.ultracards.gateway.dto.leaderboard;

public record LeaderboardEntryDTO(
        long position,
        Long userId,
        String username,
        long gamesPlayed,
        long wins,
        double winRate,
        boolean currentUser
) {
}
