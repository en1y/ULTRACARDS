package com.ultracards.gateway.dto.points;

public record PointsAchievementDTO(
        String code,
        String name,
        String metric,
        long reward,
        long current,
        long target,
        boolean earned
) {
}
