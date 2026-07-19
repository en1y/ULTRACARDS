package com.ultracards.gateway.dto.admin;

public record AdminUserPatchDTO(
        String username,
        String email,
        Boolean enabled,
        Boolean fakeAdmin,
        String status,
        String reason,
        boolean dryRun
) {
}
