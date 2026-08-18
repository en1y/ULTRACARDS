package com.ultracards.gateway.dto.admin;

import java.util.List;

/** The whole goal list, in display order: whatever is missing from it gets deleted. */
public record AdminDailyGoalsPatchDTO(List<AdminDailyGoalDTO> goals, String reason) {
}
