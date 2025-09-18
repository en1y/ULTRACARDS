package com.ultracards.gateway.dto.games;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LobbyKickRequest {
    @NotNull
    private UUID lobbyId;
    @NotNull
    private Long playerId;
}
