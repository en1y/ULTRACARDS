package com.ultracards.server.entity.games;

import com.ultracards.gateway.dto.games.GameTypeDTO;
import com.ultracards.server.entity.UserEntity;
import com.ultracards.server.entity.lobby.GameConfig;
import com.ultracards.templates.game.model.AbstractGame;
import lombok.Data;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
public class GameEntity<Game extends AbstractGame<?, ?, ?, ?, ?, ?, ?>, GameLobbyConfig extends GameConfig> {
    private UUID id = UUID.randomUUID();

    private UUID lobbyId;

    private String name;

    private UserEntity owner;

    private Instant createdAt = Instant.now();

    private List<UserEntity> players = new ArrayList<>();

    private GameTypeDTO gameType;

    private Game game;

    private Instant turnEndTime = null;

    private Integer turnDurationSeconds = null;

    private Integer turnNumber = 0;

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
