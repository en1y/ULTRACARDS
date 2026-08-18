package com.ultracards.gateway.dto.games.lobby;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record WagerConfigDTO(
        @NotNull Boolean enabled,
        @Min(0) @Max(1_000_000L) long stakePoints
) {
    public static WagerConfigDTO disabled() {
        return new WagerConfigDTO(false, 0);
    }

    public boolean isEnabled() {
        return Boolean.TRUE.equals(enabled);
    }
}
