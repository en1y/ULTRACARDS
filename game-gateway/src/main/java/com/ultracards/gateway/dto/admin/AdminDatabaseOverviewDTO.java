package com.ultracards.gateway.dto.admin;

import java.time.Instant;
import java.util.Map;

public record AdminDatabaseOverviewDTO(
        boolean available,
        String flywayVersion,
        Map<String, Long> recordsByArea,
        Instant generatedAt
) {
}
