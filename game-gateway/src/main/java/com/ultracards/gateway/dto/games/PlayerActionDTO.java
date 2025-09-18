package com.ultracards.gateway.dto.games;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PlayerActionDTO {
    @NotNull
    private UUID gameId;
    @NotBlank
    private String actionType; // e.g., PLAY_CARD, PASS, BET
    private String payloadJson; // action-specific data (card played, etc.)
}

