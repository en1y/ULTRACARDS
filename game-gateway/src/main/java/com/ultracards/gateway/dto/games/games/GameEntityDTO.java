package com.ultracards.gateway.dto.games.games;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.ultracards.gateway.dto.games.GameConfigDTO;
import com.ultracards.gateway.dto.games.GamePlayerDTO;
import com.ultracards.gateway.dto.games.GamePlayerKeyDeserializer;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class GameEntityDTO {
    @NotNull UUID id;
    @NotNull UUID lobbyId;
    @NotBlank String name;
    @NotNull private List<GamePlayerDTO> playersOrder;
    @NotNull
    @JsonDeserialize(keyUsing = GamePlayerKeyDeserializer.class)
    private Map<GamePlayerDTO, Integer> playersCardsMap;
    @NotNull private List<GameCardDTO> playedCards;
    @NotNull private int cardsLeftInDeck;
    @NotNull private GameConfigDTO gameConfig;
}
