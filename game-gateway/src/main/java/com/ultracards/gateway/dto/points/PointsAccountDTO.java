package com.ultracards.gateway.dto.points;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record PointsAccountDTO(
        long balance,
        long changeLast24Hours,
        boolean dailyClaimAvailable,
        LocalDate lastDailyClaimDate,
        Instant nextClaimAt,
        List<PointsAchievementDTO> achievements
) {
}
