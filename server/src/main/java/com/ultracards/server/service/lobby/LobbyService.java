package com.ultracards.server.service.lobby;

import com.ultracards.gateway.dto.updated.games.games.briskula.BriskulaGameConfigDTO;
import com.ultracards.gateway.dto.updated.games.lobby.*;
import com.ultracards.server.entity.UserEntity;
import com.ultracards.server.entity.lobby.LobbyEntity;
import com.ultracards.server.service.UserService;
import com.ultracards.server.service.games.GameService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static com.ultracards.gateway.dto.updated.games.lobby.GameLobbyEventDTO.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class LobbyService {
    private final LobbyManager lobbyManager;
    private final LobbyEventPublisher lobbyEventPublisher;
    private final UserService userService;
    private final GameService gameService;

    public GameLobbyDTO createLobby(UserEntity owner, GameLobbyDTO gameLobbyDTO) {
        var lobby = lobbyManager.createLobby(
                createLobbyEntity(gameLobbyDTO, owner)
        );
        return lobby.createLobbyDTO();
    }

    public Boolean joinLobby(@NotNull UUID lobbyId, UserEntity user) {
        var lobby = lobbyManager.getLobby(lobbyId);
        return lobby != null && lobby.addUser(user);
    }

    public Boolean leaveLobby(@NotNull UUID lobbyId, UserEntity user) {
        var lobby = lobbyManager.getLobby(lobbyId);
        return lobby != null && lobby.removeUser(user);
    }

    public Boolean startLobby(UserEntity user) {
        var lobby = lobbyManager.getLobby(user);
        if (lobby != null) {
            gameService.startGame(lobby);
            lobbyEventPublisher.publish(lobby, GameLobbyEventType.STARTED);
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

    public GameLobbyDTO kickPlayer(@NotNull Long playerToKickId, UserEntity user) {
        var lobby = lobbyManager.getLobby(user);
        if (lobby != null) {
            lobby.removeUser(
                    userService.getUserById(playerToKickId));
            return lobby.createLobbyDTO();
        }
        return null;
    }

    public Boolean deleteLobby(UserEntity user) {
        var lobby = lobbyManager.getLobby(user);
        if (lobby != null) {
            return lobbyManager.deleteLobby(lobby);
        }
        return false;
    }

    public List<GameLobbyDTO> getLobbies() {
        var lobbies = lobbyManager.getLobbies();
        var res =  new ArrayList<GameLobbyDTO>(lobbies.size());
        for (var l:  lobbies) {
            res.add(l.createLobbyDTO());
        }
        return res;
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
