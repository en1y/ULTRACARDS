package com.ultracards.gateway.dto.points;

import java.util.List;

/** Everything the Achievements tab renders for one player. */
public record PointsAchievementsDTO(PointsStreakDTO streak, List<PointsAchievementDTO> achievements) {
}
