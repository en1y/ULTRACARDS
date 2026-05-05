package com.ultracards.gateway.dto.games.games.briskula;

import com.ultracards.gateway.dto.games.GamePlayerDTO;
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
public class BriskulaGameHistoryDTO {
    private UUID id;
    private UUID lobbyId;
    private String name;
    private GamePlayerDTO owner;
    private Instant createdAt;
    private Instant endedAt;
    private BriskulaGameConfigDTO gameConfig;
    private GameCardDTO trumpCard;
    private List<GamePlayerDTO> playersOrder;
    private List<List<GamePlayerDTO>> teams;
    private List<BriskulaRoundHistoryDTO> rounds;
    private Map<GamePlayerDTO, Integer> finalPoints;
    private List<GamePlayerDTO> winners;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BriskulaRoundHistoryDTO {
        private Integer roundNumber;
        private Map<GamePlayerDTO, List<GameCardDTO>> playerHands;
        private List<BriskulaCardPlayHistoryDTO> plays;
        private GamePlayerDTO winner;
        private Integer points;
        private Map<GamePlayerDTO, Integer> pointsAfterRound;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BriskulaCardPlayHistoryDTO {
        private Integer playNumber;
        private GamePlayerDTO player;
        private GameCardDTO card;
    }
}
