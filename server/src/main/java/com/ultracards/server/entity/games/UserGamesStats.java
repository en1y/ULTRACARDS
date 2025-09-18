package com.ultracards.server.entity.games;

import com.ultracards.server.entity.UserEntity;
import com.ultracards.server.enums.games.GameType;
import com.ultracards.server.repositories.games.UserGamesStatsRepository;
import jakarta.persistence.*;
import lombok.*;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "user_game_stats")
@Getter @Setter
@AllArgsConstructor
@NoArgsConstructor
public class UserGamesStats {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(cascade = CascadeType.PERSIST)
    @JoinColumn(name = "user_id")
    private UserEntity user;

    @ElementCollection
    @MapKeyEnumerated(EnumType.STRING)
    @MapKeyColumn(name = "game_type")
    private Map<GameType, GameStats> gameStats = new EnumMap<>(GameType.class);

    public UserGamesStats(UserEntity user) {
        this.id = UUID.randomUUID();
        this.user = user;
        for (var gameType : GameType.values()) {
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
}

@Embeddable
@Getter @Setter
@AllArgsConstructor
@NoArgsConstructor
class GameStats {
    @Column(name = "played")
    private int played;
    @Column(name = "wins")
    private int wins;
    void addPlayed() {
        this.played++;
    }
    void addWon() {
        this.wins++;
    }
}
