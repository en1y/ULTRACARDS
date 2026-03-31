package com.ultracards.server.service.lobby;

import com.ultracards.gateway.dto.games.games.briskula.BriskulaGameConfigDTO;
import com.ultracards.gateway.dto.games.lobby.GameLobbyDTO;
import com.ultracards.gateway.dto.games.lobby.GameLobbyEventDTO;
import com.ultracards.server.entity.UserEntity;
import com.ultracards.server.entity.lobby.LobbyEntity;
import com.ultracards.server.entity.lobby.LobbyState;
import com.ultracards.server.service.UserService;
import com.ultracards.server.service.games.GameService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class LobbyService {
    private final LobbyManager lobbyManager;
    private final LobbyEventPublisher lobbyEventPublisher;
    private final UserService userService;
    private final GameService gameService;
    private final HashMap<Long, LobbyEntity> lobbyCache = new HashMap<>();

    public GameLobbyDTO createLobby(UserEntity owner, GameLobbyDTO gameLobbyDTO) {
        var lobby = lobbyManager.createLobby(
                createLobbyEntity(gameLobbyDTO, owner)
        );
        lobbyCache.put(owner.getId(), lobby);
        return lobby.createLobbyDTO();
    }

    public Boolean joinLobby(@NotNull UUID lobbyId, UserEntity user) {
        var lobby = lobbyManager.getLobby(lobbyId);
        if (lobby != null) lobbyCache.put(user.getId(), lobby);
        return lobby != null && lobby.addUser(user);
    }

    public Boolean leaveLobby(@NotNull UUID lobbyId, UserEntity user) {
        var lobby = lobbyManager.getLobby(lobbyId);
        if (lobby != null) lobbyCache.remove(user.getId());
        return lobby != null && lobby.removeUser(user);
    }

    public Boolean startLobby(UserEntity user) {
        var lobby = lobbyManager.getLobby(user);
        if (lobby != null) {
            gameService.startGame(lobby);
            lobbyEventPublisher.publish(lobby, GameLobbyEventDTO.GameLobbyEventType.STARTED);
            return true;
        }
        return false;
    }

    public GameLobbyDTO updateLobby(@Valid GameLobbyDTO lobbyDTO, UserEntity user) {
        var lobby = lobbyManager.getLobby(lobbyDTO.getId());
        if (lobby != null && lobby.getOwner().equals(user)) {
            lobby.setName(lobbyDTO.getName());
            lobby.setMinPlayers(lobbyDTO.getMinPlayers());
            lobby.setMaxPlayers(lobbyDTO.getMaxPlayers());
            try {
                var config = (BriskulaGameConfigDTO) lobbyDTO.getGameConfig();
                if (config != null) {
                    lobby.setGameConfig(config);
                }
            } catch (Exception e) {
                log.warn(e.getMessage());
            }
            return lobby.createLobbyDTO();
        }
        return null;
    }

    public GameLobbyDTO kickPlayer(@NotNull Long playerToKickId, UserEntity owner) {
        var lobby = lobbyManager.getLobby(owner);
        if (lobby != null) {
            lobby.removeUser(
                    userService.getUserById(playerToKickId));
            lobbyCache.remove(playerToKickId);
            return lobby.createLobbyDTO();
        }
        return null;
    }

    public Boolean deleteLobby(UserEntity user) {
        var lobby = lobbyManager.getLobby(user);
        if (lobby != null) {
            for (var players: lobby.getUsers()) {
                lobbyCache.remove(players.getId());
            }
            return lobbyManager.deleteLobby(lobby);
        }
        return false;
    }

    public List<GameLobbyDTO> getLobbies() {
        var lobbies = lobbyManager.getLobbies();
        var res =  new ArrayList<GameLobbyDTO>(lobbies.size());
        for (var l:  lobbies) {
            if (l.getLobbyState().equals(LobbyState.OPEN))
                res.add(l.createLobbyDTO());
        }
        return res;
    }

    public GameLobbyDTO getLobbyByUser(UserEntity user) {
        var lobby = lobbyCache.get(user.getId());
        return lobby == null ? null: lobby.createLobbyDTO();
    }

    private LobbyEntity createLobbyEntity(GameLobbyDTO gameLobbyDTO, UserEntity owner) {
        return new LobbyEntity(
                gameLobbyDTO.getName(),
                gameLobbyDTO.getGameType(),
                owner,
                gameLobbyDTO.getMinPlayers(),
                gameLobbyDTO.getMaxPlayers(),
                gameLobbyDTO.getGameConfig()
        );
    }
}
