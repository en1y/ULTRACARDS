package com.ultracards.gateway.dto.games.games.treseta;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.ultracards.gateway.dto.games.GamePlayerDTO;
import com.ultracards.gateway.dto.games.GamePlayerKeyDeserializer;
import com.ultracards.gateway.dto.games.games.GameCardDTO;
import com.ultracards.gateway.dto.games.games.GameEntityDTO;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
@NoArgsConstructor
public class TresetaGameEntityDTO extends GameEntityDTO {
    @JsonDeserialize(keyUsing = GamePlayerKeyDeserializer.class)
    private Map<GamePlayerDTO, Integer> pointsPerPerson;
    private GamePlayerDTO playersTurn;
    private Instant turnEndTime;
    private Integer turnDurationSeconds;
    private List<TresetaDeclarationDTO> declarations;
    private List<Long> canDeclareUserIds;

    public TresetaGameEntityDTO(UUID id, UUID lobbyId, String name, List<GamePlayerDTO> playersOrder,
                                Map<GamePlayerDTO, Integer> playersCardsMap, List<GameCardDTO> playedCards,
                                int cardsLeftInDeck, Map<GamePlayerDTO, Integer> pointsPerPerson,
                                GamePlayerDTO playersTurn, Instant turnEndTime, Integer turnDurationSeconds,
                                TresetaGameConfigDTO gameConfig, List<TresetaDeclarationDTO> declarations,
                                List<Long> canDeclareUserIds) {
        super(id, lobbyId, name, playersOrder, playersCardsMap, playedCards, cardsLeftInDeck, gameConfig);
        this.pointsPerPerson = pointsPerPerson;
        this.playersTurn = playersTurn;
        this.turnEndTime = turnEndTime;
        this.turnDurationSeconds = turnDurationSeconds;
        this.declarations = declarations;
        this.canDeclareUserIds = canDeclareUserIds;
    }
}
