package com.ultracards.gateway.dto.points;

public record PointsAchievementDTO(
        String code,
        long reward,
        long current,
        long target,
        boolean earned
) {
}
