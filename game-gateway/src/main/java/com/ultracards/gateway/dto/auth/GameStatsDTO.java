package com.ultracards.gateway.dto.auth;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class GameStatsDTO {
    private int played;
    private int wins;
    private Instant lastPlayedAt;

    public GameStatsDTO(int played, int wins) {
        this(played, wins, null);
    }
}
