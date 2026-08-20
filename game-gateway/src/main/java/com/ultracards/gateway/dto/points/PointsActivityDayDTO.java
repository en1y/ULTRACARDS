package com.ultracards.gateway.dto.points;

import java.time.LocalDate;

/** One square on the activity graph: the games a player finished on that day. */
public record PointsActivityDayDTO(LocalDate date, long games, long wins) {
}
