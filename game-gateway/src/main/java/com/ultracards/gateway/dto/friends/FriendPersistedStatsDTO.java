package com.ultracards.gateway.dto.friends;

import com.ultracards.gateway.dto.games.GameConfigDTO;
import com.ultracards.gateway.dto.games.GameTypeDTO;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class FriendPersistedStatsDTO {
    private GameTypeDTO gameType;
    private GameConfigDTO gameConfig;
    private String matchupType;
    private Long relatedUserId;
    private String relatedUsername;
    private int played;
    private int wins;
    private Instant lastPlayedAt;
}
