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
public class GameDTO {
    private UUID id;
    private String gameName;
    private String gameType;
    private List<Long> playerIds;
    private String stateJson;
    private boolean active;
    private Instant createdAt;
    private Instant updatedAt;
}
