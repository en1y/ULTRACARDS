package com.ultracards.gateway.dto.games;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class LobbyDTO {
    private UUID id;
    private String lobbyName;
    private String gameType;
    private Instant createdAt;
    private Long ownerId;
    private String ownerUsername;
    private List<Long> playerIds;
    private List<LobbyPlayerDTO> players;
    private Integer maxPlayers;
    private String configJson;
}
