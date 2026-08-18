package com.ultracards.gateway.dto.points;

import java.time.Instant;
import java.util.UUID;

public record PointTransactionDTO(
        UUID id,
        long amount,
        long balanceAfter,
        String type,
        String referenceId,
        Instant createdAt
) {
}
