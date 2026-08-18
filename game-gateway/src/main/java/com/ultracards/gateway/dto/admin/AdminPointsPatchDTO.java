package com.ultracards.gateway.dto.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AdminPointsPatchDTO(
        @NotNull Long amount,
        @NotBlank String reason,
        boolean dryRun
) {
}
