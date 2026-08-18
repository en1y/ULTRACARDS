package com.ultracards.gateway.dto.points;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PointEventDTO(
        UUID id,
        String name,
        String description,
        String descriptionHtml,
        Instant startsAt,
        Instant endsAt,
        Instant completedAt,
        List<String> gameTypes,
        long completionRewardPoints,
        boolean active,
        long earnedPoints,
        int hiddenAchievementCount,
        List<Achievement> achievements
) {
    public record Achievement(
            UUID id,
            String name,
            String description,
            String descriptionHtml,
            long rewardPoints,
            long games,
            long gamesRequired,
            long wins,
            long winsRequired,
            long losses,
            long lossesRequired,
            long draws,
            long drawsRequired,
            boolean completed,
            List<String> gameTypes,
            List<String> gameModes
    ) {
    }
}
