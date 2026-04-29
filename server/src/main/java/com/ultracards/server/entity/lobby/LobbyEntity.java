package com.ultracards.server.entity.lobby;

import com.ultracards.gateway.dto.games.GameConfigDTO;
import com.ultracards.gateway.dto.games.GamePlayerDTO;
import com.ultracards.gateway.dto.games.GameTypeDTO;
import com.ultracards.gateway.dto.games.lobby.GameLobbyDTO;
import com.ultracards.server.entity.UserEntity;
import com.ultracards.server.entity.games.GameEntity;
import lombok.Data;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

@Data
public class LobbyEntity {
    private UUID id;
    private String name;
    private GameTypeDTO gameType;
    private Instant createdAt;
    private List<UserEntity> users = new ArrayList<>();
    private UserEntity owner;
    private int minPlayers;
    private int maxPlayers;
    private GameConfig lobbyGameConfig;
    private LobbyState lobbyState;
    private LobbyCode lobbyCode;
    private Instant closedAt;

    public LobbyEntity(String name, GameTypeDTO gameType, UserEntity owner, int minPlayers, int maxPlayers, GameConfigDTO gameConfig, int lobbyTimer) {
        id = UUID.randomUUID();
        this.name = name;
        this.gameType = gameType;
        this.createdAt = Instant.now();
        this.owner = owner;
        users.add(owner);
        this.minPlayers = minPlayers;
        this.maxPlayers = maxPlayers;
        this.lobbyGameConfig = GameConfig.from(gameType, gameConfig, users);
        this.lobbyState = LobbyState.OPEN;
        this.closedAt = createdAt.plusSeconds(lobbyTimer);
    }

    public GameConfigDTO getGameConfig() {
        return lobbyGameConfig.toDto();
    }

    public void setGameConfig(GameConfig gameConfig) {
        this.lobbyGameConfig = gameConfig;
        if (gameType.equals(GameTypeDTO.Briskula))
            this.users = ((BriskulaLobbyGameConfig) gameConfig).getOrderedUsers();
    }

    public void setGameConfig(GameConfigDTO gameConfig) {
        this.lobbyGameConfig = GameConfig.from(gameType, gameConfig, users);
    }

    public boolean containsUser(UserEntity user) {
        return users.contains(user);
    }

    public boolean isFull() {
        return users.size() >= maxPlayers;
    }

    public boolean addUser(UserEntity user) {
        return users.contains(user) || ( users.size() < maxPlayers && !users.contains(user) && users.add(user) );
    }

    public boolean removeUser(UserEntity user) {
        return !owner.equals(user) && users.remove(user);
    }

    public GameEntity<?, ?> createGame() {
        var game = lobbyGameConfig.createGame(getId(), getName(), getOwner(), getUsers());
        lobbyState = LobbyState.STARTED;
        return game;
    }

    public GameLobbyDTO createLobbyDTO() {
        var users = new HashSet<GamePlayerDTO>();

        for (var u: getUsers()) {
            users.add(new GamePlayerDTO(u.getUsername(), u.getId()));
        }

        return new GameLobbyDTO(
                getId(),
                getName(),
                getMinPlayers(),
                getMaxPlayers(),
                users,
                new GamePlayerDTO(getOwner().getUsername(), getOwner().getId()),
                getGameType(),
                getLobbyCode().lobbyCode(),
                getGameConfig(),
                closedAt
        );
    }
}
