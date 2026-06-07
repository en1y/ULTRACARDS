package com.ultracards.gateway.dto.auth;

import com.ultracards.gateway.dto.games.GameConfigDTO;
import com.ultracards.gateway.dto.games.GameTypeDTO;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class BriskulaMatchupStatsDTO {
    private GameTypeDTO gameType;
    private GameConfigDTO gameConfig;
    private Long relatedUserId;
    private String relatedUsername;
    private int played;
    private int wins;
    private Instant lastPlayedAt;
}
