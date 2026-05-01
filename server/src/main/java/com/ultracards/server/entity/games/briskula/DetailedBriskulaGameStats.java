package com.ultracards.server.entity.games.briskula;

import com.ultracards.games.briskula.BriskulaGameConfig;
import com.ultracards.server.entity.games.gamestats.DetailedGameStats;
import com.ultracards.server.entity.games.gamestats.GameStats;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.EnumMap;
import java.util.Map;

@Embeddable
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
public class DetailedBriskulaGameStats implements DetailedGameStats {

    @ElementCollection
    @CollectionTable(
            name = "user_briskula_game_stats_entries",
            joinColumns = @JoinColumn(name = "user_game_stats_id")
    )
    @AttributeOverrides({
            @AttributeOverride(name = "played", column = @Column(name = "played", nullable = false)),
            @AttributeOverride(name = "wins", column = @Column(name = "wins", nullable = false))
    })
    @MapKeyEnumerated(EnumType.STRING)
    @MapKeyColumn(name = "briskula_game_config")
    private Map<BriskulaGameConfig, GameStats> briskulaStats = new EnumMap<>(BriskulaGameConfig.class);

    public void addGamePlayed(BriskulaGameConfig gameConfig) {
        briskulaStats.computeIfAbsent(gameConfig, ignored -> new GameStats()).addPlayed();
    }

    public void addGameWon(BriskulaGameConfig gameConfig) {
        briskulaStats.computeIfAbsent(gameConfig, ignored -> new GameStats()).addWon();
    }

    public int getGamesPlayed(BriskulaGameConfig gameConfig) {
        return briskulaStats.getOrDefault(gameConfig, new GameStats()).getPlayed();
    }

    public int getGamesWon(BriskulaGameConfig gameConfig) {
        return briskulaStats.getOrDefault(gameConfig, new GameStats()).getWins();
    }
}
