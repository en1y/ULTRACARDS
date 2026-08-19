package com.ultracards.gateway.dto.admin;

import java.time.LocalDate;

/**
 * An audited streak correction. Every field is optional: whatever is left null keeps
 * the value the player already has.
 */
public record AdminStreakPatchDTO(Integer currentStreak, Integer longestStreak, LocalDate lastPlayedDate,
                                  Integer freezes, String reason) {
}
