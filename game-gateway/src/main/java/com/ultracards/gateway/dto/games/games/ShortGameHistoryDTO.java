package com.ultracards.gateway.dto.games.games;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.ultracards.gateway.dto.games.GameConfigDTO;
import com.ultracards.gateway.dto.games.GamePlayerDTO;
import com.ultracards.gateway.dto.games.GamePlayerKeyDeserializer;
import com.ultracards.gateway.dto.games.GameTypeDTO;
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
public class ShortGameHistoryDTO {
    private UUID id;
    private UUID lobbyId;
    private String name;
    private GameTypeDTO gameType;
    private Instant createdAt;
    private Instant endedAt;
    private GameConfigDTO gameConfig;
    private List<GamePlayerDTO> playersOrder;
    @JsonDeserialize(keyUsing = GamePlayerKeyDeserializer.class)
    private Map<GamePlayerDTO, Integer> pointsByUser;
    private List<GamePlayerDTO> winners;
    /** Net economy Points the requesting user took from this game — bet plus rewards. */
    private Long pointsDelta;

    /** History builders do not know whose Points to report; the controller fills that in. */
    public ShortGameHistoryDTO(UUID id, UUID lobbyId, String name, GameTypeDTO gameType, Instant createdAt,
                               Instant endedAt, GameConfigDTO gameConfig, List<GamePlayerDTO> playersOrder,
                               Map<GamePlayerDTO, Integer> pointsByUser, List<GamePlayerDTO> winners) {
        this(id, lobbyId, name, gameType, createdAt, endedAt, gameConfig, playersOrder, pointsByUser, winners, null);
    }
}
