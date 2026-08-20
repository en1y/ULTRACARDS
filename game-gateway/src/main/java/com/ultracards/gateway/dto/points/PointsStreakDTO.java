package com.ultracards.gateway.dto.points;

import java.time.LocalDate;

/**
 * A player's daily play streak.
 *
 * @param freezes      unspent streak freezes, capped at 3
 * @param atRisk       the streak survives today only if the player plays, or a freeze covers the gap
 * @param freezesToKeep freezes the next play would spend to bridge the missed days
 */
public record PointsStreakDTO(int current, int longest, int freezes, LocalDate lastPlayedDate,
                              boolean playedToday, boolean atRisk, int freezesToKeep) {
}
