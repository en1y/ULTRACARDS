package com.ultracards.server.entity.games.gamestats;

import com.ultracards.games.briskula.BriskulaGameConfig;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

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

    public boolean matches(String gameConfig, Long relatedUserId) {
        return this.gameConfig.equals(gameConfig)
                && this.relatedUserId.equals(relatedUserId);
    }

    public void addPlayed() {
        played++;
    }

    public void addWin() {
        wins++;
    }

    public static String configKey(BriskulaGameConfig gameConfig) {
        return gameConfig.name();
    }
}
