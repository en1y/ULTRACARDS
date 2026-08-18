package com.ultracards.gateway.dto.admin;

public record AdminPointsSettingsPatchDTO(Integer wagerFeePercent, Long startingBalance, Long dailyReward,
                                          String reason) {
}
