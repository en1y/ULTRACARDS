package com.ultracards.server.service.lobby;

import com.ultracards.gateway.dto.games.GameTypeDTO;
import com.ultracards.gateway.dto.games.lobby.GameLobbyDTO;
import com.ultracards.gateway.dto.games.lobby.GameLobbyEventDTO;
import com.ultracards.server.entity.UserEntity;
import com.ultracards.server.entity.games.GameEntity;
import com.ultracards.server.entity.lobby.LobbyEntity;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class LobbyManager {
    private final Map<UUID, LobbyEntity> lobbiesById = new ConcurrentHashMap<>();
    private final Map<Long, LobbyEntity> lobbiesByUser = new ConcurrentHashMap<>();
    private final Map<GameTypeDTO, List<LobbyEntity>> lobbiesByGameType = new ConcurrentHashMap<>();
    private final Map<UUID, LobbyEntity> lobbyByGameId = new ConcurrentHashMap<>();
    @Getter
    private final List<LobbyEntity> lobbies = Collections.synchronizedList(new ArrayList<>());

    private final LobbyEventPublisher lobbyEventPublisher;

    @Value("${app.lobby.timer.duration-seconds}")
    private int lobbyTimer;

    public LobbyManager(LobbyEventPublisher lobbyEventPublisher) {
        this.lobbyEventPublisher = lobbyEventPublisher;
        for (var gt: GameTypeDTO.values()) {
            lobbiesByGameType.put(gt, Collections.synchronizedList(new ArrayList<>()));
        }
    }

    public LobbyEntity getLobby(UUID lobbyId) {
        return lobbiesById.get(lobbyId);
    }

    public LobbyEntity getLobby(UserEntity owner) {
        return lobbiesByUser.get(owner.getId());
    }

    public LobbyEntity getByGame(UUID gameId) {
        return lobbyByGameId.get(gameId);
    }

    public LobbyEntity createLobby(GameLobbyDTO gameLobbyDTO, UserEntity owner) {
        var lobby = new LobbyEntity(
                gameLobbyDTO.getName(),
                gameLobbyDTO.getGameType(),
                owner,
                gameLobbyDTO.getMinPlayers(),
                gameLobbyDTO.getMaxPlayers(),
                gameLobbyDTO.getGameConfig(),
                lobbyTimer
        );
        return createLobby(lobby);
    }

    public LobbyEntity createLobby(LobbyEntity lobby) {
        put(lobby);
        return lobby;
    }
    public Boolean deleteLobby(LobbyEntity lobby) {
        return remove(lobby);
    }

    public void putGame(LobbyEntity lobby, GameEntity<?> game) {
        lobbyByGameId.put(game.getId(), lobby);
    }

    private Boolean remove(LobbyEntity lobby) {
        var l = lobbiesByUser.get(lobby.getOwner().getId());

        if (l != null) {
            lobbiesById.remove(l.getId());
            lobbiesByGameType.get(l.getGameType()).remove(l);
            lobbyByGameId.remove(l.getId());
            lobbies.remove(l);
            lobbiesByUser.remove(l.getOwner().getId());
            lobbyEventPublisher.publish(l, GameLobbyEventDTO.GameLobbyEventType.DELETED);
        }

        return l != null;
    }

    private void put(LobbyEntity lobby) {
        remove(lobby);

        lobbiesById.put(lobby.getId(), lobby);
        lobbiesByUser.put(lobby.getOwner().getId(), lobby);
        lobbiesByGameType.get(lobby.getGameType()).add(lobby);
        lobbies.add(lobby);
        lobbyEventPublisher.publish(lobby, GameLobbyEventDTO.GameLobbyEventType.CREATED);
    }
}
