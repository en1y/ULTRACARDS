package com.ultracards.server.entity.games.gamestats;

import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Embeddable
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
public class GameStats {
    private int played;
    private int wins;
    private Instant lastPlayedAt;

    public GameStats(int played, int wins) {
        this(played, wins, null);
    }

    public void addPlayed() {
        this.played++;
        this.lastPlayedAt = Instant.now();
    }

    public void addWon() {
        this.wins++;
    }
}
