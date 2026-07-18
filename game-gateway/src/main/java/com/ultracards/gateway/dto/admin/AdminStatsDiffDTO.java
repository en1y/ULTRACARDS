package com.ultracards.gateway.dto.admin;

public record AdminStatsDiffDTO(AdminStatsDTO before, AdminStatsDTO after, String warning, boolean dryRun) {
}
