package com.ultracards.server.entity.games.gamestats;

import com.ultracards.games.briskula.BriskulaGameConfig;
import com.ultracards.server.enums.games.GameType;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
@EqualsAndHashCode(of = {"gameType", "gameConfig", "relatedUserId"})
public class UserMatchupStats {
    @Enumerated(EnumType.STRING)
    @Column(name = "game_type", nullable = false)
    private GameType gameType;

    @Column(name = "game_config", nullable = false)
    private String gameConfig;

    @Column(name = "related_user_id", nullable = false)
    private Long relatedUserId;

    @Column(name = "played", nullable = false)
    private int played;

    @Column(name = "wins", nullable = false)
    private int wins;

    public boolean matches(GameType gameType, String gameConfig, Long relatedUserId) {
        return this.gameType == gameType
                && this.gameConfig.equals(gameConfig)
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

    public static String noConfigKey() {
        return "";
    }
}
