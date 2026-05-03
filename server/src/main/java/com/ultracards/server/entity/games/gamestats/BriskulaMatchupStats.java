package com.ultracards.server.entity.games.gamestats;

import com.ultracards.games.briskula.BriskulaGameConfig;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = {"gameConfig", "relatedUserId"})
public class BriskulaMatchupStats {
    private String gameConfig;
    private Long relatedUserId;
    private int played;
    private int wins;
    private Instant lastPlayedAt;

    public BriskulaMatchupStats(String gameConfig, Long relatedUserId, int played, int wins) {
        this(gameConfig, relatedUserId, played, wins, null);
    }

    public boolean matches(String gameConfig, Long relatedUserId) {
        return this.gameConfig.equals(gameConfig)
                && this.relatedUserId.equals(relatedUserId);
    }

    public void addPlayed() {
        played++;
        lastPlayedAt = Instant.now();
    }

    public void addWin() {
        wins++;
    }

    public static String configKey(BriskulaGameConfig gameConfig) {
        return gameConfig.name();
    }
}
