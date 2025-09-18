package com.ultracards.gateway.dto.games;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class LobbyJoinRequest {
    @NotNull
    private UUID lobbyId;
}

