package com.ultracards.server.entity.games.gamestats;

import com.ultracards.games.briskula.BriskulaGameConfig;
import com.ultracards.server.entity.UserEntity;
import com.ultracards.server.enums.games.GameType;
import jakarta.persistence.*;
import lombok.*;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "user_game_stats")
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class UserGamesStats {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(cascade = CascadeType.PERSIST)
    @JoinColumn(name = "user_id")
    private UserEntity user;

    @ElementCollection
    @CollectionTable(
            name = "user_game_stats_entries",
            joinColumns = @JoinColumn(name = "user_game_stats_id")
    )
    @AttributeOverrides({
            @AttributeOverride(name = "played", column = @Column(name = "played", nullable = false)),
            @AttributeOverride(name = "wins", column = @Column(name = "wins", nullable = false))
    })
    @MapKeyEnumerated(EnumType.STRING)
    @MapKeyColumn(name = "game_type")
    private Map<GameType, GameStats> gameStats = new EnumMap<>(GameType.class);

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

    public UserGamesStats(UserEntity user) {
        this.user = user;
        for (GameType gameType : GameType.values()) {
            gameStats.put(gameType, new GameStats(0, 0));
        }
    }

    public void addGamePlayed(GameType gameType) {
        if (!gameStats.containsKey(gameType)) {
            gameStats.put(gameType, new GameStats());
        }
        gameStats.get(gameType).addPlayed();
    }

    public void addGameWon(GameType gameType) {
        if (!gameStats.containsKey(gameType)) {
            gameStats.put(gameType, new GameStats());
        }
        gameStats.get(gameType).addWon();
    }

    public int getGamesWon(GameType gameType) {
        return gameStats.get(gameType).getWins();
    }

    public int getGamesPlayed(GameType gameType) {
        return gameStats.get(gameType).getPlayed();
    }

    public void addBriskulaGamePlayed(BriskulaGameConfig gameConfig) {
        addGamePlayed(GameType.BRISKULA);
        briskulaStats.computeIfAbsent(gameConfig, ignored -> new GameStats()).addPlayed();
    }

    public void addBriskulaGameWon(BriskulaGameConfig gameConfig) {
        addGameWon(GameType.BRISKULA);
        briskulaStats.computeIfAbsent(gameConfig, ignored -> new GameStats()).addWon();
    }

    public int getBriskulaGamesPlayed(BriskulaGameConfig gameConfig) {
        return briskulaStats.getOrDefault(gameConfig, new GameStats()).getPlayed();
    }

    public int getBriskulaGamesWon(BriskulaGameConfig gameConfig) {
        return briskulaStats.getOrDefault(gameConfig, new GameStats()).getWins();
    }
}
