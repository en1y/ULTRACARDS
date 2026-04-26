package com.ultracards.gateway.dto.games.games.briskula;

import com.fasterxml.jackson.annotation.JsonTypeName;
import com.ultracards.gateway.dto.games.GameConfigDTO;
import com.ultracards.gateway.dto.games.GamePlayerDTO;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonTypeName("Briskula")
public class BriskulaGameConfigDTO implements GameConfigDTO {
    @NotNull private Integer numberOfPlayers;
    @NotNull private Integer cardsInHandNum;
    @NotNull private Boolean teamsEnabled;
    @NotNull private List<GamePlayerDTO> team1;
    @NotNull private List<GamePlayerDTO> team2;
}
