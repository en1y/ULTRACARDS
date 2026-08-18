package com.ultracards.gateway.dto.admin;

public record AdminPointsAdjustmentDTO(
        Long userId,
        long previousBalance,
        long newBalance,
        long amount,
        boolean dryRun
) {
}
