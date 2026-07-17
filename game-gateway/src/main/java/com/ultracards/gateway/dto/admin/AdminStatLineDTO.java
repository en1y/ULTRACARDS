package com.ultracards.gateway.dto.admin;

import java.time.Instant;

public record AdminStatLineDTO(int played, int wins, Instant lastPlayedAt) {
}
