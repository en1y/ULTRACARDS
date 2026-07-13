package com.ultracards.gateway.dto.games.games.briskula;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.ultracards.gateway.dto.games.GamePlayerDTO;
import com.ultracards.gateway.dto.games.GamePlayerKeyDeserializer;
import com.ultracards.gateway.dto.games.games.GameCardDTO;
import com.ultracards.gateway.dto.games.games.GameEntityDTO;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
@NoArgsConstructor
public class BriskulaGameEntityDTO extends GameEntityDTO {
    @Getter @Setter
    @JsonDeserialize(keyUsing = GamePlayerKeyDeserializer.class)
    private Map<GamePlayerDTO, Integer> pointsPerPerson;
    @Getter @Setter
    private GamePlayerDTO playersTurn;
    @Getter @Setter
    private Instant turnEndTime;
    @Getter @Setter
    private Integer turnDurationSeconds;
    @Getter @Setter
    private GameCardDTO trumpCard;

    public BriskulaGameEntityDTO(UUID id, UUID lobbyId, String name, List<GamePlayerDTO> playersOrder, Map<GamePlayerDTO, Integer> playersCardsMap, List<GameCardDTO> playedCards, int cardsLeftInDeck, Map<GamePlayerDTO, Integer> pointsPerPerson, GamePlayerDTO playersTurn, Instant turnEndTime, Integer turnDurationSeconds, GameCardDTO trumpCard, BriskulaGameConfigDTO gameConfig) {
        super(id, lobbyId, name, playersOrder, playersCardsMap, playedCards, cardsLeftInDeck, gameConfig);
        this.pointsPerPerson = pointsPerPerson;
        this.playersTurn = playersTurn;
        this.turnEndTime = turnEndTime;
        this.turnDurationSeconds = turnDurationSeconds;
        this.trumpCard = trumpCard;
    }
}
