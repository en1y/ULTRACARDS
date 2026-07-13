package com.ultracards.gateway.dto.games.games.treseta;

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
public class TresetaGameHistoryDTO {
    private UUID id;
    private UUID lobbyId;
    private String name;
    private GamePlayerDTO owner;
    private Instant createdAt;
    private Instant endedAt;
    private TresetaGameConfigDTO gameConfig;
    private List<GamePlayerDTO> playersOrder;
    private List<List<GamePlayerDTO>> teams;
    private List<TresetaRoundHistoryDTO> rounds;
    @JsonDeserialize(keyUsing = GamePlayerKeyDeserializer.class)
    private Map<GamePlayerDTO, Integer> finalPoints;
    private List<GamePlayerDTO> winners;

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class TresetaRoundHistoryDTO {
        private Integer roundNumber;
        @JsonDeserialize(keyUsing = GamePlayerKeyDeserializer.class)
        private Map<GamePlayerDTO, List<GameCardDTO>> playerHands;
        private List<TresetaCardPlayHistoryDTO> plays;
        private GamePlayerDTO winner;
        private Integer points;
        @JsonDeserialize(keyUsing = GamePlayerKeyDeserializer.class)
        private Map<GamePlayerDTO, Integer> pointsAfterRound;
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class TresetaCardPlayHistoryDTO {
        private Integer playNumber;
        private GamePlayerDTO player;
        private GameCardDTO card;
    }
}
