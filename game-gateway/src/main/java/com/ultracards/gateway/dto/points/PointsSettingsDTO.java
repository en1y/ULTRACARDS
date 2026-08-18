package com.ultracards.gateway.dto.points;

public record PointsSettingsDTO(int wagerFeePercent, long startingBalance, long dailyReward) {
}
