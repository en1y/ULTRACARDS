package com.ultracards.gateway.dto.admin;

public record AdminDashboardDTO(
        AdminOverviewDTO overview,
        AdminStatusDTO status,
        AdminDatabaseOverviewDTO database
) {
}
