package com.ultracards.gateway.dto.leaderboard;

import com.ultracards.gateway.dto.games.GameTypeDTO;

import java.util.List;

public record LeaderboardPageDTO(
        List<LeaderboardEntryDTO> items,
        int page,
        int size,
        long totalElements,
        int totalPages,
        Long currentUserPosition,
        int minimumGames,
        LeaderboardMetricDTO metric,
        GameTypeDTO gameType,
        String mode,
        List<String> availableModes
) {
}
