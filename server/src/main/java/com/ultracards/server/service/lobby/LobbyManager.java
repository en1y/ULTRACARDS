package com.ultracards.server.service.lobby;

import com.ultracards.gateway.dto.updated.games.GameTypeDTO;
import com.ultracards.server.entity.UserEntity;
import com.ultracards.server.entity.lobby.LobbyEntity;
import lombok.Getter;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static com.ultracards.gateway.dto.updated.games.lobby.GameLobbyEventDTO.*;

@Service
public class LobbyManager {
    private final Map<UUID, LobbyEntity> lobbiesById = new ConcurrentHashMap<>();
    private final Map<Long, LobbyEntity> lobbiesByUser = new ConcurrentHashMap<>();
    private final Map<GameTypeDTO, List<LobbyEntity>> lobbiesByGameType = new ConcurrentHashMap<>();
    @Getter
    private final List<LobbyEntity> lobbies = Collections.synchronizedList(new ArrayList<>());

    private final LobbyEventPublisher lobbyEventPublisher;

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

    public LobbyEntity createLobby(LobbyEntity lobby) {
        put(lobby);
        return lobby;
    }


    public Boolean deleteLobby(LobbyEntity lobby) {
        return remove(lobby);
    }

    private Boolean remove(LobbyEntity lobby) {
        var l = lobbiesByUser.get(lobby.getOwner().getId());

        if (l != null) {
            lobbiesById.remove(l.getId());
            lobbiesByGameType.get(l.getGameType()).remove(l);
            lobbies.remove(l);
            lobbyEventPublisher.publish(l, GameLobbyEventType.DELETED);
        }

        return l != null;
    }

    private void put(LobbyEntity lobby) {
        remove(lobby);

        lobbiesById.put(lobby.getId(), lobby);
        lobbiesByUser.put(lobby.getOwner().getId(), lobby);
        lobbiesByGameType.get(lobby.getGameType()).add(lobby);
        lobbies.add(lobby);
        lobbyEventPublisher.publish(lobby, GameLobbyEventType.CREATED);
    }
}
