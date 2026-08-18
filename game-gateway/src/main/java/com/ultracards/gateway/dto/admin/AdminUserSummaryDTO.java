package com.ultracards.gateway.dto.admin;

import java.time.Instant;
import java.util.Set;

public record AdminUserSummaryDTO(
        Long id,
        String email,
        String username,
        boolean enabled,
        boolean fakeAdmin,
        String status,
        Set<String> roles,
        Instant createdAt,
        Instant updatedAt,
        Instant lastLoginAt,
        long points,
        long pointsChangeLast7Days
) {
    public AdminUserSummaryDTO(Long id, String email, String username, boolean enabled, boolean fakeAdmin,
                               String status, Set<String> roles, Instant createdAt, Instant updatedAt,
                               Instant lastLoginAt) {
        this(id, email, username, enabled, fakeAdmin, status, roles, createdAt, updatedAt,
                lastLoginAt, 0, 0);
    }
}
