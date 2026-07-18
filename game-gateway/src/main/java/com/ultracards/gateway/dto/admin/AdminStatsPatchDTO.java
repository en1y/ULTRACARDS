package com.ultracards.gateway.dto.admin;

import java.time.Instant;

public record AdminStatsPatchDTO(Integer played, Integer wins, Instant lastPlayedAt, String reason, boolean dryRun) {
}
