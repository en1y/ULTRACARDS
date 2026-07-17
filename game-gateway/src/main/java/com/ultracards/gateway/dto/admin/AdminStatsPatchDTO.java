package com.ultracards.gateway.dto.admin;

import java.time.Instant;

public record AdminStatsPatchDTO(int played, int wins, Instant lastPlayedAt, String reason, boolean dryRun) {
}
