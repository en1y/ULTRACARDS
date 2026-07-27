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
public class DurakMatchupStatsDTO {
    private GameTypeDTO gameType;
    private GameConfigDTO gameConfig;
    private String modeKey;
    private Long relatedUserId;
    private String relatedUsername;
    private int played;
    /** Games where the subject was not the durak and the related user was. */
    private int wins;
    private Instant lastPlayedAt;
}
