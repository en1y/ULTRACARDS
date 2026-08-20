package com.ultracards.gateway.dto.admin;

import java.util.List;

/** The whole achievement list, in display order: whatever is missing from it gets deleted. */
public record AdminAchievementsPatchDTO(List<AdminAchievementDTO> achievements, String reason) {
}
