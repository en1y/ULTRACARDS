package com.ultracards.server.entity.games.gamestats;

import com.ultracards.games.briskula.BriskulaGameConfig;
import jakarta.persistence.Column;
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
    @Column(name = "game_config", nullable = false)
    private String gameConfig;

    @Column(name = "related_user_id", nullable = false)
    private Long relatedUserId;

    @Column(name = "played", nullable = false)
    private int played;

    @Column(name = "wins", nullable = false)
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
