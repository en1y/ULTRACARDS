package com.ultracards.server.service.lobby;

import com.ultracards.games.briskula.BriskulaGameConfig;
import com.ultracards.gateway.dto.games.GameTypeDTO;
import com.ultracards.gateway.dto.games.games.briskula.BriskulaGameConfigDTO;
import com.ultracards.gateway.dto.games.lobby.GameLobbyDTO;
import com.ultracards.server.entity.UserEntity;
import com.ultracards.server.entity.lobby.BriskulaLobbyGameConfig;
import com.ultracards.server.entity.lobby.TresetaLobbyGameConfig;
import com.ultracards.gateway.dto.games.games.treseta.TresetaGameConfigDTO;
import com.ultracards.games.treseta.TresetaGameConfig;
import com.ultracards.server.entity.lobby.LobbyCode;
import com.ultracards.server.entity.lobby.LobbyEntity;
import com.ultracards.server.entity.lobby.LobbyState;
import com.ultracards.server.service.friends.FriendService;
import com.ultracards.server.service.users.UserService;
import com.ultracards.server.service.chat.ChatService;
import com.ultracards.server.service.games.GameService;
import com.ultracards.server.service.notifications.NotificationService;
import com.ultracards.server.service.ultrakill.UltrakillLevelService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

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
    private final UltrakillLevelService ultrakillLevelService;
    private final NotificationService notificationService;
    private final FriendService friendService;

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
            UltrakillLevelService ultrakillLevelService,
            NotificationService notificationService,
            FriendService friendService,
            LobbyEventPublisher eventPublisher,
            @Qualifier("timer") TaskScheduler taskScheduler
    ) {
        this.lobbyManager = lobbyManager;
        this.userService = userService;
        this.gameService = gameService;
        this.chatService = chatService;
        this.ultrakillLevelService = ultrakillLevelService;
        this.notificationService = notificationService;
        this.friendService = friendService;
        this.eventPublisher = eventPublisher;
        this.taskScheduler = taskScheduler;
    }

    public GameLobbyDTO createLobby(UserEntity owner, GameLobbyDTO gameLobbyDTO) {
        if (getLobbyByUser(owner) != null)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "You are already in a lobby");

        var levelNumbers = ultrakillLevelService.findLevelNumbers(gameLobbyDTO.getName(), 1);
        if (levelNumbers.length > 0)
            gameLobbyDTO.setName(String.format("%s: %s", levelNumbers[0],
                    ultrakillLevelService.getLevelName(levelNumbers[0])));

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

        if (lobby != null
                && lobby.getLobbyState().equals(LobbyState.PRIVATE)
                && !lobby.containsUser(user)
                && !notificationService.hasGameInvite(user, lobbyId))
            return JoinLobbyResult.NOT_FOUND;

        return getJoinLobbyResult(user, lobby);
    }

    public JoinLobbyResult joinLobby(@NotNull String lobbyCode, UserEntity user) {
        var lobby = lobbyManager.getLobby(new LobbyCode(lobbyCode.toUpperCase()));
        return getJoinLobbyResult(user, lobby);
    }

    private JoinLobbyResult getJoinLobbyResult(UserEntity user, LobbyEntity lobby) {
        if (lobby == null || lobby.isStarted()) {
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
        if (lobby != null && lobby.getOwner().equals(user) && hasRequiredPlayers(lobby)) {
            gameService.startGame(lobby);
            lobby.setStarted(true);
            eventPublisher.publish(lobby, STARTED);
            return true;
        }
        return false;
    }

    private boolean hasRequiredPlayers(LobbyEntity lobby) {
        var config = lobby.getLobbyGameConfig();
        if (config instanceof BriskulaLobbyGameConfig briskula)
            return lobby.getUsers().size() == briskula.getGameConfig().getNumberOfPlayers();
        if (config instanceof TresetaLobbyGameConfig treseta)
            return lobby.getUsers().size() == treseta.getGameConfig().getNumberOfPlayers();
        return lobby.getUsers().size() >= lobby.getMinPlayers();
    }

    public void inviteFriendToLobby(UserEntity user, Long friendUserId) {
        var friend = friendService.getActiveFriend(user, friendUserId);

        if (friendService.isBlocked(friend, user))
            throw new ResponseStatusException(HttpStatus.CONFLICT,"This user is not accepting invites from you");

        var lobby = getLobbyByUser(user);
        if (lobby == null)
            throw new ResponseStatusException(HttpStatus.CONFLICT,"You must be in a lobby to invite a friend");

        notificationService.createGameInviteNotification(user, friend, lobby.getId());
    }

    public GameLobbyDTO updateLobby(@Valid GameLobbyDTO lobbyDTO, UserEntity user) {
        var lobby = lobbyManager.getLobby(lobbyDTO.getId());
        if (lobby != null && lobby.getOwner().equals(user)) {
            return applyUpdate(lobby, lobbyDTO);
        }
        return null;
    }

    public GameLobbyDTO updateLobby(UUID lobbyId, String name, Boolean isPublic, String mode) {
        var lobby = lobbyManager.getLobby(lobbyId);
        if (lobby == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Lobby not found");
        if (lobby.isStarted()) throw new ResponseStatusException(HttpStatus.CONFLICT, "Started lobbies cannot be updated");
        var dto = lobby.createLobbyDTO(true);
        if (name != null) dto.setName(name);
        if (isPublic != null) dto.setIsPublic(isPublic);
        if (mode != null) applyMode(dto, mode);
        return applyUpdate(lobby, dto);
    }

    private GameLobbyDTO applyUpdate(LobbyEntity lobby, GameLobbyDTO lobbyDTO) {
        var config = lobbyDTO.getGameConfig();
        if ((config instanceof BriskulaGameConfigDTO briskulaConfig
                && briskulaConfig.getNumberOfPlayers() < lobby.getUsers().size())
                || (config instanceof TresetaGameConfigDTO tresetaConfig
                && tresetaConfig.getNumberOfPlayers() < lobby.getUsers().size()))
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Kick a player before reducing the game mode player count");

        var previousUsers = new ArrayList<>(lobby.getUsers());
        lobby.setName(lobbyDTO.getName());
        lobby.setMinPlayers(lobbyDTO.getMinPlayers());
        lobby.setMaxPlayers(lobbyDTO.getMaxPlayers());
        if (lobbyDTO.getIsPublic() != null)
            lobby.setLobbyState(lobbyDTO.getIsPublic() ? LobbyState.PUBLIC : LobbyState.PRIVATE);

        if (config != null) {
            if (config instanceof BriskulaGameConfigDTO briskulaConfig)
                lobby.setGameConfig(BriskulaLobbyGameConfig.fromDto(briskulaConfig, lobby.getUsers(), lobby.getOwner()));
            else if (config instanceof TresetaGameConfigDTO tresetaConfig)
                lobby.setGameConfig(TresetaLobbyGameConfig.fromDto(tresetaConfig, lobby.getUsers(), lobby.getOwner()));
            else lobby.setGameConfig(config);
        }
        removeStaleLobbyCacheEntries(lobby, previousUsers);
        eventPublisher.publish(lobby, UPDATED);
        return lobby.createLobbyDTO(true);
    }

    private void applyMode(GameLobbyDTO dto, String mode) {
        try {
            if (dto.getGameType() == GameTypeDTO.Briskula) {
                var config = BriskulaGameConfig.valueOf(mode.trim().toUpperCase());
                dto.setMinPlayers(config.getNumberOfPlayers());
                dto.setMaxPlayers(config.getNumberOfPlayers());
                dto.setGameConfig(new BriskulaGameConfigDTO(config.getNumberOfPlayers(), config.getCardsInHandNum(),
                        config.areTeamsEnabled(), null));
                return;
            }
            if (dto.getGameType() == GameTypeDTO.Treseta) {
                var config = TresetaGameConfig.valueOf(mode.trim().toUpperCase());
                dto.setMinPlayers(config.getNumberOfPlayers());
                dto.setMaxPlayers(config.getNumberOfPlayers());
                dto.setGameConfig(new TresetaGameConfigDTO(config.getNumberOfPlayers(), config.getCardsInHandNum(),
                        config.areTeamsEnabled(), null));
                return;
            }
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "This game type has no configurable modes");
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown lobby mode: " + mode);
        }
    }

    public GameLobbyDTO kickPlayer(@NotNull Long playerToKickId, UserEntity owner) {
        var lobby = lobbyManager.getLobby(owner);
        if (lobby != null) {
            var player = userService.getUserById(playerToKickId);
            var removed = lobby.removeUser(player);
            if (removed) {
                syncLobbyConfig(lobby);
                lobbyCache.remove(playerToKickId);
                eventPublisher.publishKicked(player, lobby.getId());
                eventPublisher.publish(lobby, UPDATED);
                return lobby.createLobbyDTO(true);
            }
        }
        return null;
    }

    public GameLobbyDTO kickPlayer(UUID lobbyId, Long playerToKickId) {
        var lobby = lobbyManager.getLobby(lobbyId);
        if (lobby == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Lobby not found");
        if (lobby.getOwner().getId().equals(playerToKickId))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "The lobby owner cannot be kicked; close the lobby instead");
        var player = userService.getUserById(playerToKickId);
        if (player == null || !lobby.removeUser(player))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Player is not in this lobby");
        syncLobbyConfig(lobby);
        lobbyCache.remove(playerToKickId);
        eventPublisher.publishKicked(player, lobby.getId());
        eventPublisher.publish(lobby, UPDATED);
        return lobby.createLobbyDTO(true);
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
            if (res) {
                notificationService.deleteGameInviteNotifications(lobby.getId());
                eventPublisher.publish(lobby, DELETED);
            }
            return res;
        }
        return false;
    }

    public Boolean openLobby(LobbyEntity lobby) {
        if (lobby.isStarted()) lobby.setStarted(false);
        var closedAt = Instant.now().plusSeconds(lobbyTimer);
        scheduleClose(lobby, closedAt);
        return true;
    }

    public GameLobbyDTO extendLobby(UUID lobbyId, long seconds) {
        var lobby = lobbyManager.getLobby(lobbyId);
        if (lobby == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Lobby not found");
        if (lobby.isStarted()) throw new ResponseStatusException(HttpStatus.CONFLICT, "Started lobbies cannot be extended");
        if (seconds < 60 || seconds > 86_400)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Extension must be between 60 and 86400 seconds");
        var base = lobby.getClosedAt() != null && lobby.getClosedAt().isAfter(Instant.now())
                ? lobby.getClosedAt() : Instant.now();
        scheduleClose(lobby, base.plusSeconds(seconds));
        eventPublisher.publish(lobby, UPDATED);
        return lobby.createLobbyDTO(true);
    }

    private void scheduleClose(LobbyEntity lobby, Instant closedAt) {
        lobby.setClosedAt(closedAt);
        taskScheduler.schedule(() -> {
            // stale timers from previous openLobby calls see a newer closedAt and do nothing
            if (!lobby.isStarted() && closedAt.equals(lobby.getClosedAt()))
                deleteLobby(lobby);
        }, closedAt);
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

    public List<GameLobbyDTO> getAllLobbiesForAdministration() {
        var lobbies = lobbyManager.getLobbies();
        var output = new ArrayList<GameLobbyDTO>(lobbies.size());
        synchronized (lobbies) {
            for (var lobby : lobbies) output.add(lobby.createLobbyDTO(true));
        }
        return output;
    }

    public LobbyEntity getLobbyEntity(UUID lobbyId) {
        return lobbyManager.getLobby(lobbyId);
    }

    public Boolean deleteLobby(UUID lobbyId) {
        return deleteLobby(lobbyManager.getLobby(lobbyId));
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
            case "treseta" -> {
                var lobbies = lobbyManager.getLobbies(GameTypeDTO.Treseta);
                var output = new ArrayList<GameLobbyDTO>();
                var configs = TresetaGameConfig.values();
                if (gameSettingId == null || gameSettingId < 0 || gameSettingId >= configs.length) yield null;
                var config = configs[gameSettingId];
                for (var lobby : lobbies)
                    if (lobby.getLobbyState().equals(LobbyState.PUBLIC)
                            && ((TresetaLobbyGameConfig) lobby.getLobbyGameConfig()).getGameConfig().equals(config))
                        output.add(lobby.createLobbyDTO(false));
                yield output;
            }
            case "durak", "poker" -> new ArrayList<>();
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
        } else if (config instanceof TresetaGameConfigDTO tresetaConfig) {
            lobby.setLobbyGameConfig(TresetaLobbyGameConfig.fromDto(tresetaConfig, lobby.getUsers(), lobby.getOwner()));
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
