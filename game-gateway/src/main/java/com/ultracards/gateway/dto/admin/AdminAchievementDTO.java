package com.ultracards.gateway.dto.admin;

/** A one-time achievement as the admin edits it. `code` is null for one that does not exist yet. */
public record AdminAchievementDTO(String code, String name, String metric, Long target, Long rewardPoints,
                                  Boolean enabled) {
}
