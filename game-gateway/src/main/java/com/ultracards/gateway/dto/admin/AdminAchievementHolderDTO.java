package com.ultracards.gateway.dto.admin;

import java.time.Instant;

/** A player who earned one achievement, and what it paid them at the time. */
public record AdminAchievementHolderDTO(Long userId, String username, String email, Instant earnedAt,
                                        long rewardPoints) {
}
