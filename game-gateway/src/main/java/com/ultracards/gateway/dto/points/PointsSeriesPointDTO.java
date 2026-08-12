package com.ultracards.gateway.dto.points;

import java.time.Instant;

/** One plotted balance, taken from the ledger's stored balance-after. */
public record PointsSeriesPointDTO(Instant at, long balance, long change, String reason) {
}
