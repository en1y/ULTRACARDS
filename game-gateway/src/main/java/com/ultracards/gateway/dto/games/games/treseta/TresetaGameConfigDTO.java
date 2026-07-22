package com.ultracards.gateway.dto.games.games.treseta;

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
@JsonTypeName("Treseta")
public class TresetaGameConfigDTO implements GameConfigDTO {
    @NotNull private Integer numberOfPlayers;
    @NotNull private Integer cardsInHandNum;
    @NotNull private Boolean teamsEnabled;
    private Boolean declarationsEnabled;
    private List<GamePlayerDTO> orderedUsers;

    public TresetaGameConfigDTO(Integer numberOfPlayers, Integer cardsInHandNum, Boolean teamsEnabled,
                                List<GamePlayerDTO> orderedUsers) {
        this(numberOfPlayers, cardsInHandNum, teamsEnabled, false, orderedUsers);
    }

    public boolean areDeclarationsEnabled() {
        return Boolean.TRUE.equals(declarationsEnabled);
    }
}
