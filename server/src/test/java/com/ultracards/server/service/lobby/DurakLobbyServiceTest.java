package com.ultracards.server.service.lobby;

import com.ultracards.games.durak.DurakGameConfig;
import com.ultracards.games.durak.DurakThrowInPolicy;
import com.ultracards.gateway.dto.games.GameTypeDTO;
import com.ultracards.gateway.dto.games.games.durak.DurakGameConfigDTO;
import com.ultracards.gateway.dto.games.games.durak.DurakThrowInPolicyDTO;
import com.ultracards.gateway.dto.games.lobby.GameLobbyDTO;
import com.ultracards.server.entity.UserEntity;
import com.ultracards.server.entity.lobby.DurakLobbyGameConfig;
import com.ultracards.server.entity.lobby.LobbyEntity;
import com.ultracards.server.entity.lobby.LobbyState;
import com.ultracards.server.service.chat.ChatService;
import com.ultracards.server.service.friends.FriendService;
import com.ultracards.server.service.games.GameAvailabilityService;
import com.ultracards.server.service.games.GameService;
import com.ultracards.server.service.notifications.NotificationService;
import com.ultracards.server.service.ultrakill.UltrakillLevelService;
import com.ultracards.server.service.users.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class DurakLobbyServiceTest {

    private final LobbyManager lobbyManager = mock(LobbyManager.class);
    private final UserService userService = mock(UserService.class);
    private final GameService gameService = mock(GameService.class);
    private final GameAvailabilityService gameAvailabilityService = mock(GameAvailabilityService.class);
    private final ChatService chatService = mock(ChatService.class);
    private final UltrakillLevelService ultrakillLevelService = mock(UltrakillLevelService.class);
    private final NotificationService notificationService = mock(NotificationService.class);
    private final FriendService friendService = mock(FriendService.class);
    private final LobbyEventPublisher eventPublisher = mock(LobbyEventPublisher.class);
    private final TaskScheduler taskScheduler = mock(TaskScheduler.class);

    private LobbyService lobbyService;

    @BeforeEach
    void setUp() {
        lobbyService = new LobbyService(lobbyManager, userService, gameService, gameAvailabilityService, chatService,
                ultrakillLevelService, notificationService, friendService, eventPublisher, taskScheduler);
    }

    private static UserEntity user(Long id, String name) {
        var user = new UserEntity(name + "@example.com", name);
        user.setId(id);
        return user;
    }

    private static DurakGameConfigDTO config(int players, int deckSize, boolean jokers, boolean passing) {
        return new DurakGameConfigDTO(players, deckSize, jokers, DurakThrowInPolicyDTO.EVERYONE, passing, null);
    }

    private LobbyEntity lobby(UserEntity owner, DurakGameConfigDTO config) {
        var players = config.getNumberOfPlayers();
        var lobby = new LobbyEntity("Durak", GameTypeDTO.Durak, owner, players, players, config,
                LobbyState.PUBLIC, 60);
        lobby.setLobbyCode(new com.ultracards.server.entity.lobby.LobbyCode("TESTCODE"));
        when(lobbyManager.getLobby(lobby.getId())).thenReturn(lobby);
        return lobby;
    }

    @Test
    void createsEveryDeckPresetWithTheOwnerSeated() {
        var owner = user(1L, "Owner");
        for (var preset : List.of(config(2, 24, false, false), config(3, 36, false, true),
                config(4, 54, false, true), config(4, 54, true, false))) {
            var lobby = lobby(owner, preset);
            var stored = (DurakLobbyGameConfig) lobby.getLobbyGameConfig();
            assertThat(stored.getOrderedUsers()).containsExactly(owner);
            assertThat(stored.getGameConfig().deckSize()).isEqualTo(preset.getDeckSize());
            assertThat(stored.getGameConfig().jokersEnabled()).isEqualTo(preset.getJokersEnabled());
            assertThat(lobby.getGameConfig()).isInstanceOf(DurakGameConfigDTO.class);
            assertThat(((DurakGameConfigDTO) lobby.getGameConfig()).getModeKey())
                    .isEqualTo(stored.getGameConfig().modeKey());
        }
    }

    @Test
    void rejectsIncompatibleDeckAndPlayerCombinationsWithBadRequest() {
        var owner = user(1L, "Owner");
        assertThatThrownBy(() -> lobby(owner, config(6, 24, false, false)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThatThrownBy(() -> lobby(owner, config(3, 36, true, false)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void startRequiresExactlyTheConfiguredPlayerCount() {
        var owner = user(1L, "Owner");
        var lobby = lobby(owner, config(3, 36, false, true));
        when(lobbyManager.getLobby(owner)).thenReturn(lobby);

        assertThat(lobbyService.startLobby(owner)).isFalse();
        verify(gameService, never()).startGame(lobby);

        lobby.addUser(user(2L, "Two"));
        assertThat(lobbyService.startLobby(owner)).isFalse();

        lobby.addUser(user(3L, "Three"));
        assertThat(lobbyService.startLobby(owner)).isTrue();
        verify(gameService).startGame(lobby);
    }

    @Test
    void reducingSeatsBelowOccupantsIsAConflict() {
        var owner = user(1L, "Owner");
        var lobby = lobby(owner, config(3, 36, false, true));
        lobby.addUser(user(2L, "Two"));
        lobby.addUser(user(3L, "Three"));

        var update = new GameLobbyDTO();
        update.setId(lobby.getId());
        update.setName("Durak");
        update.setGameConfig(config(2, 36, false, true));

        assertThatThrownBy(() -> lobbyService.updateLobby(update, owner))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Kick a player")
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
        assertThat(lobby.getUsers()).hasSize(3);
    }

    @Test
    void bothTogglesCanBeUpdatedWhileTheSeatCountStaysTheSame() {
        var owner = user(1L, "Owner");
        var lobby = lobby(owner, config(2, 54, false, false));

        var update = new GameLobbyDTO();
        update.setId(lobby.getId());
        update.setName("Durak");
        update.setMinPlayers(2);
        update.setMaxPlayers(2);
        update.setGameConfig(new DurakGameConfigDTO(2, 54, true, DurakThrowInPolicyDTO.NEIGHBORS_ONLY, true, null));

        var result = lobbyService.updateLobby(update, owner);

        var stored = (DurakLobbyGameConfig) lobby.getLobbyGameConfig();
        assertThat(stored.getGameConfig()).isEqualTo(new DurakGameConfig(2, 54, true,
                DurakThrowInPolicy.NEIGHBORS_ONLY, true));
        assertThat(((DurakGameConfigDTO) result.getGameConfig()).getModeKey())
                .isEqualTo("P2_D54_JOKERS_NEIGHBORS_PASS");
    }

    @Test
    void durakCapacityAlwaysMatchesTheConfiguredPlayerCount() {
        var owner = user(1L, "Owner");
        var lobby = lobby(owner, config(2, 36, false, false));
        var update = new GameLobbyDTO();
        update.setId(lobby.getId());
        update.setName("Four seats");
        update.setGameType(GameTypeDTO.Durak);
        update.setMinPlayers(1);
        update.setMaxPlayers(6);
        update.setGameConfig(config(4, 36, false, false));

        var result = lobbyService.updateLobby(update, owner);

        assertThat(lobby.getMinPlayers()).isEqualTo(4);
        assertThat(lobby.getMaxPlayers()).isEqualTo(4);
        assertThat(result.getMinPlayers()).isEqualTo(4);
        assertThat(result.getMaxPlayers()).isEqualTo(4);
    }

    @Test
    void creationNormalizesClientSuppliedDurakCapacity() {
        var owner = user(1L, "Owner");
        var request = new GameLobbyDTO();
        request.setName("Durak");
        request.setGameType(GameTypeDTO.Durak);
        request.setIsPublic(true);
        request.setMinPlayers(1);
        request.setMaxPlayers(6);
        request.setGameConfig(config(4, 36, false, false));
        when(ultrakillLevelService.findLevelNumbers("Durak", 1)).thenReturn(new String[0]);
        when(lobbyManager.createLobby(any(GameLobbyDTO.class), eq(owner))).thenAnswer(invocation -> {
            GameLobbyDTO normalized = invocation.getArgument(0);
            var created = new LobbyEntity(normalized.getName(), normalized.getGameType(), owner,
                    normalized.getMinPlayers(), normalized.getMaxPlayers(), normalized.getGameConfig(),
                    LobbyState.PUBLIC, 60);
            created.setLobbyCode(new com.ultracards.server.entity.lobby.LobbyCode("TESTCODE"));
            return created;
        });

        var result = lobbyService.createLobby(owner, request);

        assertThat(request.getMinPlayers()).isEqualTo(4);
        assertThat(request.getMaxPlayers()).isEqualTo(4);
        assertThat(result.getMinPlayers()).isEqualTo(4);
        assertThat(result.getMaxPlayers()).isEqualTo(4);
    }

    @Test
    void adminModeSwitchAcceptsOnlyCanonicalModeKeys() {
        var owner = user(1L, "Owner");
        var lobby = lobby(owner, config(2, 24, false, false));

        var result = lobbyService.updateLobby(lobby.getId(), null, null, "P4_D54_JOKERS_EVERYONE_PASS");

        assertThat(result.getMinPlayers()).isEqualTo(4);
        assertThat(result.getMaxPlayers()).isEqualTo(4);
        assertThat(((DurakGameConfigDTO) result.getGameConfig()).getModeKey())
                .isEqualTo("P4_D54_JOKERS_EVERYONE_PASS");

        assertThatThrownBy(() -> lobbyService.updateLobby(lobby.getId(), null, null, "P4_D54"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Unknown lobby mode");
    }

    @Test
    void publicLobbyFilteringUsesTheStableModeOrdering() {
        var owner = user(1L, "Owner");
        var lobby = lobby(owner, config(2, 24, false, false));
        when(lobbyManager.getLobbies(GameTypeDTO.Durak)).thenReturn(List.of(lobby));
        var configs = DurakGameConfig.validConfigs();
        var index = configs.indexOf(new DurakGameConfig(2, 24, false, DurakThrowInPolicy.EVERYONE, false));

        assertThat(lobbyService.getLobbies("durak", index)).hasSize(1);
        assertThat(lobbyService.getLobbies("durak", index + 1)).isEmpty();
        assertThat(lobbyService.getLobbies("durak", configs.size())).isNull();
        assertThat(lobbyService.getLobbies("durak", -1)).isNull();
    }

    @Test
    void aDisabledDurakModeCannotBeCreatedOrStarted() {
        var owner = user(1L, "Owner");
        var disabled = new ResponseStatusException(HttpStatus.CONFLICT, "Game availability is disabled for DURAK");
        doThrow(disabled).when(gameAvailabilityService)
                .requireEnabled(eq(GameTypeDTO.Durak), any(DurakGameConfigDTO.class));

        var request = new GameLobbyDTO();
        request.setGameType(GameTypeDTO.Durak);
        request.setGameConfig(config(2, 36, false, false));
        assertThatThrownBy(() -> lobbyService.createLobby(owner, request)).isSameAs(disabled);

        var lobby = lobby(owner, config(2, 36, false, false));
        lobby.addUser(user(2L, "Two"));
        when(lobbyManager.getLobby(owner)).thenReturn(lobby);
        assertThatThrownBy(() -> lobbyService.startLobby(owner)).isSameAs(disabled);
        verify(gameService, never()).startGame(lobby);
    }
}
