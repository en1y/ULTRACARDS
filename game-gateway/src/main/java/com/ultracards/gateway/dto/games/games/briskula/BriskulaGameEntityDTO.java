package com.ultracards.gateway.dto.games.games.briskula;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.ultracards.gateway.dto.games.GamePlayerDTO;
import com.ultracards.gateway.dto.games.GamePlayerKeyDeserializer;
import com.ultracards.gateway.dto.games.games.GameCardDTO;
import com.ultracards.gateway.dto.games.games.GameEntityDTO;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public class BriskulaGameEntityDTO extends GameEntityDTO {
    @Getter @Setter
    @JsonDeserialize(keyUsing = GamePlayerKeyDeserializer.class)
    private Map<GamePlayerDTO, Integer> pointsPerPerson;
    @Getter @Setter
    private GamePlayerDTO playersTurn;
    @Getter @Setter
    private GameCardDTO trumpCard;

    public BriskulaGameEntityDTO(UUID id, UUID lobbyId, String name, Map<GamePlayerDTO, Integer> playersCardsMap, List<GameCardDTO> playedCards, int cardsLeftInDeck, Map<GamePlayerDTO, Integer> pointsPerPerson, GamePlayerDTO playersTurn, GameCardDTO trumpCard) {
        super(id, lobbyId, name, playersCardsMap, playedCards, cardsLeftInDeck);
        this.pointsPerPerson = pointsPerPerson;
        this.playersTurn = playersTurn;
        this.trumpCard = trumpCard;
    }
}
