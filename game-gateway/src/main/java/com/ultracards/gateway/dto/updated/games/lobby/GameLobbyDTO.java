package com.ultracards.gateway.dto.updated.games.lobby;

import com.ultracards.gateway.dto.updated.games.GameConfigDTO;
import com.ultracards.gateway.dto.updated.games.GamePlayerDTO;
import com.ultracards.gateway.dto.updated.games.GameTypeDTO;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;
import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class GameLobbyDTO {
    @NotNull private UUID id;
    @NotBlank private String name;
    @NotNull private Integer minPlayers;
    @NotNull private Integer maxPlayers;
    @NotNull private Set<GamePlayerDTO> players;
    @NotNull private GamePlayerDTO host;
    @NotNull private GameTypeDTO gameType;
    @NotNull private GameConfigDTO gameConfig;
}
