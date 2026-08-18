package com.ultracards.gateway.dto.admin;

/** A daily goal as the admin edits it. `code` is null for a goal that does not exist yet. */
public record AdminDailyGoalDTO(String code, String name, String metric, Long target, Long rewardPoints,
                                Boolean enabled) {
}
