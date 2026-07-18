package com.ultracards.gateway.dto.admin;

public record AdminUserPatchDTO(
        String username,
        String email,
        Boolean enabled,
        String status,
        String reason,
        boolean dryRun
) {
}
