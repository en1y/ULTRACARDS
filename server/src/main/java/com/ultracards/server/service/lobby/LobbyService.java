package com.ultracards.server.service.lobby;

import com.ultracards.gateway.dto.games.games.briskula.BriskulaGameConfigDTO;
import com.ultracards.gateway.dto.games.lobby.GameLobbyDTO;
import com.ultracards.server.entity.UserEntity;
import com.ultracards.server.entity.lobby.LobbyEntity;
import com.ultracards.server.entity.lobby.LobbyState;
import com.ultracards.server.service.UserService;
import com.ultracards.server.service.chat.ChatService;
import com.ultracards.server.service.games.GameService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.function.Function;

import static com.ultracards.gateway.dto.games.lobby.GameLobbyEventDTO.GameLobbyEventType.*;

@Slf4j
@Service
public class LobbyService {
    public enum JoinLobbyResult {
        JOINED,
        FULL,
        NOT_FOUND
    }

    private final LobbyManager lobbyManager;
    private final UserService userService;
    private final GameService gameService;
    private final ChatService chatService;
    private final TaskScheduler taskScheduler;
    private final HashMap<Long, LobbyEntity> lobbyCache = new HashMap<>();
    private final LobbyEventPublisher eventPublisher;

    @Value("${app.lobby.timer.duration-seconds}")
    private int lobbyTimer;

    public LobbyService(
            LobbyManager lobbyManager,
            UserService userService,
            GameService gameService,
            ChatService chatService,
            LobbyEventPublisher eventPublisher,
            @Qualifier("timer") TaskScheduler taskScheduler
    ) {
        this.lobbyManager = lobbyManager;
        this.userService = userService;
        this.gameService = gameService;
        this.chatService = chatService;
        this.eventPublisher = eventPublisher;
        this.taskScheduler = taskScheduler;
    }

    public GameLobbyDTO createLobby(UserEntity owner, GameLobbyDTO gameLobbyDTO) {
        var lobby = lobbyManager.createLobby(gameLobbyDTO, owner);
        lobbyCache.put(owner.getId(), lobby);
        chatService.createChat(lobby.getId());
        openLobby(lobby);
        eventPublisher.publish(lobby, CREATED);
        return lobby.createLobbyDTO();
    }

    public JoinLobbyResult joinLobby(@NotNull UUID lobbyId, UserEntity user) {
        var lobby = lobbyManager.getLobby(lobbyId);
        if (lobby == null) {
            return JoinLobbyResult.NOT_FOUND;
        }

        if (lobby.isFull() && !lobby.containsUser(user)) {
            return JoinLobbyResult.FULL;
        }

        if (lobby.addUser(user)) {
            lobbyCache.put(user.getId(), lobby);
            eventPublisher.publish(lobby, UPDATED);
            return JoinLobbyResult.JOINED;
        }

        return JoinLobbyResult.FULL;
    }

    public Boolean leaveLobby(@NotNull UUID lobbyId, UserEntity user) {
        var lobby = lobbyManager.getLobby(lobbyId);
        var success = false;
        if (lobby != null) {
            success = lobby.removeUser(user);
            lobbyCache.remove(user.getId());
            eventPublisher.publish(lobby, UPDATED);
        }
        return lobby != null && success;
    }

    public Boolean startLobby(UserEntity user) {
        var lobby = lobbyManager.getLobby(user);
        if (lobby != null) {
            gameService.startGame(lobby);
            lobby.setLobbyState(LobbyState.STARTED);
            eventPublisher.publish(lobby, STARTED);
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
            eventPublisher.publish(lobby, UPDATED);
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
            eventPublisher.publish(lobby, UPDATED);
            return lobby.createLobbyDTO();
        }
        return null;
    }

    public Boolean deleteLobby(UserEntity user) {
        return deleteLobby(lobbyManager.getLobby(user));
    }

    public Boolean deleteLobby(LobbyEntity lobby) {
        if (lobby != null) {
            for (var players: lobby.getUsers()) {
                lobbyCache.remove(players.getId());
            }
            chatService.deleteChat(lobby.getId());
            lobby.setLobbyState(LobbyState.CLOSED);
            var res = lobbyManager.deleteLobby(lobby);
            if (res) eventPublisher.publish(lobby, DELETED);
            return res;
        }
        return false;
    }

    public Boolean openLobby(LobbyEntity lobby) {
        lobby.setLobbyState(LobbyState.OPEN);
        lobby.setClosedAt(Instant.now().plusSeconds(lobbyTimer));
        taskScheduler.schedule(() -> {
            if(lobby.getLobbyState().equals(LobbyState.OPEN))
                deleteLobby(lobby.getOwner());
        }, lobby.getClosedAt());
        return true;
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

    public LobbyEntity getLobbyByUser(UserEntity user) {
        return lobbyCache.get(user.getId());
    }

    @Bean(name = "openLobby")
    public Function<LobbyEntity, Boolean> openLobbyFunction() {
        return this::openLobby;
    }

}
