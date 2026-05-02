package com.ultracards.server.entity.games.gamestats;

import com.ultracards.server.entity.UserEntity;
import com.ultracards.server.enums.games.GameType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

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

    public UserGamesStats(UserEntity user) {
        this.user = user;
        for (GameType gameType : GameType.values()) {
            gameStats.put(gameType, new GameStats(0, 0));
        }
    }

    public void addGame(GameType gameType, boolean won) {
        var stats = gameStats.computeIfAbsent(gameType, ignored -> new GameStats());
        stats.addPlayed();
        if (won) {
            stats.addWon();
        }
    }

    public int getGamesWon(GameType gameType) {
        return gameStats.getOrDefault(gameType, new GameStats()).getWins();
    }

    public int getGamesPlayed(GameType gameType) {
        return gameStats.getOrDefault(gameType, new GameStats()).getPlayed();
    }
}
