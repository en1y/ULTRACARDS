package com.ultracards.server.service.lobby;

import com.ultracards.games.briskula.BriskulaGameConfig;
import com.ultracards.gateway.dto.games.GameTypeDTO;
import com.ultracards.gateway.dto.games.games.briskula.BriskulaGameConfigDTO;
import com.ultracards.gateway.dto.games.lobby.GameLobbyDTO;
import com.ultracards.server.entity.UserEntity;
import com.ultracards.server.entity.lobby.BriskulaLobbyGameConfig;
import com.ultracards.server.entity.lobby.LobbyCode;
import com.ultracards.server.entity.lobby.LobbyEntity;
import com.ultracards.server.entity.lobby.LobbyState;
import com.ultracards.server.service.UserService;
import com.ultracards.server.service.chat.ChatService;
import com.ultracards.server.service.games.GameService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
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
        syncLobbyConfig(lobby);
        lobbyCache.put(owner.getId(), lobby);
        chatService.createChat(lobby.getId());
        openLobby(lobby);
        eventPublisher.publish(lobby, CREATED);
        return lobby.createLobbyDTO(true);
    }

    public JoinLobbyResult joinLobby(@NotNull UUID lobbyId, UserEntity user) {
        var lobby = lobbyManager.getLobby(lobbyId);

        if (lobby.getLobbyState().equals(LobbyState.PRIVATE))
            return JoinLobbyResult.NOT_FOUND;

        return getJoinLobbyResult(user, lobby);
    }

    public JoinLobbyResult joinLobby(@NotNull String lobbyCode, UserEntity user) {
        var lobby = lobbyManager.getLobby(new LobbyCode(lobbyCode.toUpperCase()));
        return getJoinLobbyResult(user, lobby);
    }

    private JoinLobbyResult getJoinLobbyResult(UserEntity user, LobbyEntity lobby) {
        if (lobby == null) {
            return JoinLobbyResult.NOT_FOUND;
        }

        if (lobby.isFull() && !lobby.containsUser(user)) {
            return JoinLobbyResult.FULL;
        }

        if (lobby.addUser(user)) {
            syncLobbyConfig(lobby);
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
            if (success) {
                syncLobbyConfig(lobby);
                eventPublisher.publish(lobby, UPDATED);
            }
        }
        return lobby != null && success;
    }

    public Boolean startLobby(UserEntity user) {
        var lobby = lobbyManager.getLobby(user);
        if (lobby != null && lobby.getUsers().size() >= lobby.getMinPlayers()) {
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
            var previousUsers = new ArrayList<>(lobby.getUsers());
            lobby.setName(lobbyDTO.getName());
            lobby.setMinPlayers(lobbyDTO.getMinPlayers());
            lobby.setMaxPlayers(lobbyDTO.getMaxPlayers());
            try {
                var config = lobbyDTO.getGameConfig();
                if (config != null) {
                    if (config instanceof BriskulaGameConfigDTO briskulaConfig) {
                        lobby.setGameConfig(BriskulaLobbyGameConfig.fromDto(briskulaConfig, lobby.getUsers(), lobby.getOwner()));
                    } else {
                        lobby.setGameConfig(config);
                    }
                }
            } catch (Exception e) {
                log.warn(e.getMessage());
            }
            removeStaleLobbyCacheEntries(lobby, previousUsers);
            eventPublisher.publish(lobby, UPDATED);
            return lobby.createLobbyDTO(true);
        }
        return null;
    }

    public GameLobbyDTO kickPlayer(@NotNull Long playerToKickId, UserEntity owner) {
        var lobby = lobbyManager.getLobby(owner);
        if (lobby != null) {
            var removed = lobby.removeUser(userService.getUserById(playerToKickId));
            if (removed) {
                syncLobbyConfig(lobby);
                lobbyCache.remove(playerToKickId);
                eventPublisher.publish(lobby, UPDATED);
                return lobby.createLobbyDTO(true);
            }
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
        lobby.setLobbyState(LobbyState.PUBLIC);
        lobby.setClosedAt(Instant.now().plusSeconds(lobbyTimer));
        taskScheduler.schedule(() -> {
            if(lobby.getLobbyState().equals(LobbyState.PUBLIC))
                deleteLobby(lobby.getOwner());
        }, lobby.getClosedAt());
        return true;
    }

    public List<GameLobbyDTO> getLobbies() {
        var lobbies = lobbyManager.getLobbies();
        var res =  new ArrayList<GameLobbyDTO>(lobbies.size());
        for (var l: lobbies) {
            if (l.getLobbyState().equals(LobbyState.PUBLIC))
                res.add(l.createLobbyDTO(false));
        }
        return res;
    }

    public List<GameLobbyDTO> getLobbies(String gameType, Integer gameSettingId) {
        return switch (gameType.toLowerCase()) {
            case "briskula" -> {
                var lobbies = lobbyManager.getLobbies(GameTypeDTO.Briskula);
                var res = new ArrayList<GameLobbyDTO>();
                var briskulaConfigs = BriskulaGameConfig.values();

                if (gameSettingId == null || gameSettingId < 0 ||  gameSettingId >= briskulaConfigs.length)
                    yield null;
                var config = briskulaConfigs[gameSettingId];

                for (var l: lobbies)
                    if (l.getLobbyState().equals(LobbyState.PUBLIC) &&
                        ((BriskulaLobbyGameConfig)l.getLobbyGameConfig()).getGameConfig().equals(config))
                            res.add(l.createLobbyDTO(false));

                yield res;
            }
            case "treseta", "durak", "poker" -> new ArrayList<>();
            default -> null;
        };
    }

    public LobbyEntity getLobbyByUser(UserEntity user) {
        var lobby = lobbyCache.get(user.getId());
        if (lobby != null && !lobby.containsUser(user)) {
            lobbyCache.remove(user.getId());
            return null;
        }
        return lobby;
    }

    private void syncLobbyConfig(LobbyEntity lobby) {
        if (lobby == null) {
            return;
        }

        var config = lobby.getGameConfig();
        if (config instanceof BriskulaGameConfigDTO briskulaConfig) {
            lobby.setLobbyGameConfig(BriskulaLobbyGameConfig.fromDto(briskulaConfig, lobby.getUsers(), lobby.getOwner()));
        }
    }

    private void removeStaleLobbyCacheEntries(LobbyEntity lobby, List<UserEntity> previousUsers) {
        for (var previousUser : previousUsers) {
            if (!lobby.containsUser(previousUser)) {
                lobbyCache.remove(previousUser.getId());
            }
        }
    }

    @Bean(name = "openLobby")
    public Function<LobbyEntity, Boolean> openLobbyFunction() {
        return this::openLobby;
    }

}
