package com.ultracards.server.entity.games.gamestats;

import com.ultracards.games.briskula.BriskulaGameConfig;
import com.ultracards.server.entity.UserEntity;
import com.ultracards.server.entity.games.briskula.DetailedBriskulaGameStats;
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

    @Embedded
    private DetailedBriskulaGameStats detailedBriskulaGameStats = new DetailedBriskulaGameStats();

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
        detailedBriskulaGameStats.addGamePlayed(gameConfig);
    }

    public void addBriskulaGameWon(BriskulaGameConfig gameConfig) {
        addGameWon(GameType.BRISKULA);
        detailedBriskulaGameStats.addGameWon(gameConfig);
    }

    public int getBriskulaGamesPlayed(BriskulaGameConfig gameConfig) {
        return detailedBriskulaGameStats.getGamesPlayed(gameConfig);
    }

    public int getBriskulaGamesWon(BriskulaGameConfig gameConfig) {
        return detailedBriskulaGameStats.getGamesWon(gameConfig);
    }
}
