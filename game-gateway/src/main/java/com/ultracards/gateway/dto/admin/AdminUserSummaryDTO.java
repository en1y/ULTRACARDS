package com.ultracards.gateway.dto.admin;

import java.time.Instant;
import java.util.Set;

public record AdminUserSummaryDTO(
        Long id,
        String email,
        String username,
        boolean enabled,
        String status,
        Set<String> roles,
        Instant createdAt,
        Instant updatedAt,
        Instant lastLoginAt
) {
}
