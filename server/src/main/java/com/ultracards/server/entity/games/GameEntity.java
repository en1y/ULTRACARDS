package com.ultracards.server.entity.games;

import com.ultracards.gateway.dto.games.GameTypeDTO;
import com.ultracards.server.entity.UserEntity;
import com.ultracards.server.entity.lobby.GameConfig;
import com.ultracards.templates.game.model.AbstractGame;
import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Transient;
import lombok.Data;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@MappedSuperclass
@Data
public class GameEntity<Game extends AbstractGame<?, ?, ?, ?, ?, ?, ?>, GameLobbyConfig extends GameConfig> {
    @Id
    @Column(nullable = false, updatable = false)
    private UUID id = UUID.randomUUID();

    @Column(name = "lobby_id", nullable = false, updatable = false)
    private UUID lobbyId;

    @Column(nullable = false)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false)
    private UserEntity owner;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "briskula_game_players",
            joinColumns = @JoinColumn(name = "briskula_game_id"),
            inverseJoinColumns = @JoinColumn(name = "user_id")
    )
    @OrderColumn(name = "player_order")
    private List<UserEntity> players = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(name = "game_type", nullable = false, length = 50)
    private GameTypeDTO gameType;

    @Transient
    private Game game;

    @Column(name = "turn_end_time")
    private Instant turnEndTime = null;

    @Column(name = "turn_duration_seconds")
    private Integer turnDurationSeconds = null;

    @Column(name = "turn_number", nullable = false)
    private Integer turnNumber = 0;

    @Transient
    private GameLobbyConfig gameConfig;

    protected GameEntity() {
    }

    public GameEntity(UUID lobbyId, String name, UserEntity owner, List<UserEntity> players, GameTypeDTO gameType, Game game, GameLobbyConfig gameConfig) {
        this.lobbyId = lobbyId;
        this.name = name;
        this.owner = owner;
        this.players.addAll(players);
        this.gameType = gameType;
        this.game = game;
        this.gameConfig = gameConfig;
    }

    public boolean isActive() {
        return getGame().isGameActive();
    }
}
