package com.ultracards.gateway.dto.games.lobby;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.ultracards.gateway.dto.games.GameConfigDTO;
import com.ultracards.gateway.dto.games.GamePlayerDTO;
import com.ultracards.gateway.dto.games.GameTypeDTO;
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
    private UUID id;
    @NotBlank private String name;
    @NotNull private Integer minPlayers;
    @NotNull private Integer maxPlayers;
    @NotNull private Set<GamePlayerDTO> players;
    @NotNull private GamePlayerDTO host;
    @NotNull private GameTypeDTO gameType;
    @JsonTypeInfo(
            use = JsonTypeInfo.Id.NAME,
            include = JsonTypeInfo.As.EXTERNAL_PROPERTY,
            property = "gameType"
    )
    @NotNull private GameConfigDTO gameConfig;
}
