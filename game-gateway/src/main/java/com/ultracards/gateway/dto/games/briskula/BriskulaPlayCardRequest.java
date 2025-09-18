package com.ultracards.gateway.dto.games.briskula;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BriskulaPlayCardRequest {
    @NotBlank
    private String suit;
    @NotBlank
    private String value;
}
