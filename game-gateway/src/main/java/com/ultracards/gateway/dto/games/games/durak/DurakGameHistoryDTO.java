package com.ultracards.gateway.dto.games.games.durak;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.ultracards.gateway.dto.games.GamePlayerDTO;
import com.ultracards.gateway.dto.games.GamePlayerKeyDeserializer;
import com.ultracards.gateway.dto.games.games.GameCardDTO;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DurakGameHistoryDTO {
    private UUID id;
    private UUID lobbyId;
    private String name;
    private GamePlayerDTO owner;
    private Instant createdAt;
    private Instant endedAt;
    private DurakGameConfigDTO gameConfig;
    private List<GamePlayerDTO> playersOrder;
    private String trumpSuit;
    private GameCardDTO trumpIndicator;
    private List<DurakBoutHistoryDTO> bouts;
    private List<GamePlayerDTO> finishOrder;
    private GamePlayerDTO loser;
    private List<GamePlayerDTO> winners;
    private boolean draw;

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class DurakBoutHistoryDTO {
        private Integer boutNumber;
        @JsonDeserialize(keyUsing = GamePlayerKeyDeserializer.class)
        private Map<GamePlayerDTO, List<GameCardDTO>> startingHands;
        private GamePlayerDTO initialAttacker;
        private GamePlayerDTO finalDefender;
        private Integer maxAttackCards;
        private List<DurakPlayHistoryDTO> plays;
        private List<GamePlayerDTO> passChain;
        /** DEFENDED or TAKEN. */
        private String outcome;
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class DurakPlayHistoryDTO {
        private Integer playNumber;
        /** ATTACK, DEFEND, THROW_IN or PASS. */
        private String actionType;
        private GamePlayerDTO player;
        private GameCardDTO card;
        /** For DEFEND: the play number of the attack this card covers. */
        private Integer targetPlayNumber;
    }
}
