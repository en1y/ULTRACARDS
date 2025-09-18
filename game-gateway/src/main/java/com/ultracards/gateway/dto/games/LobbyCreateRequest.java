package com.ultracards.gateway.dto.games;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class LobbyCreateRequest {
    @NotNull
    private String gameType; // BRISKULA, POKER, etc.
    @NotBlank
    private String lobbyName;
    // Optional JSON config string specific to the game, e.g., BriskulaGameConfig
    private String configJson;
    private Integer maxPlayers;
}
