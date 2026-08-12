package com.ultracards.server.service.lobby;

import com.ultracards.server.entity.UserEntity;
import com.ultracards.server.entity.lobby.LobbyEntity;
import com.ultracards.server.entity.lobby.LobbyState;
import com.ultracards.server.entity.lobby.TresetaLobbyGameConfig;
import com.ultracards.games.treseta.TresetaGameConfig;
import com.ultracards.gateway.dto.games.GameTypeDTO;
import com.ultracards.gateway.dto.games.games.briskula.BriskulaGameConfigDTO;
import com.ultracards.gateway.dto.games.lobby.GameLobbyDTO;
import com.ultracards.gateway.dto.games.lobby.WagerConfigDTO;
import com.ultracards.server.service.chat.ChatService;
import com.ultracards.server.service.friends.FriendService;
import com.ultracards.server.service.games.GameService;
import com.ultracards.server.service.games.GameAvailabilityService;
import com.ultracards.server.service.notifications.NotificationService;
import com.ultracards.server.service.points.PointsService;
import com.ultracards.server.service.ultrakill.UltrakillLevelService;
import com.ultracards.server.service.users.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

import static com.ultracards.gateway.dto.games.lobby.GameLobbyEventDTO.GameLobbyEventType.DELETED;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

class LobbyServiceTest {

    private final LobbyManager lobbyManager = mock(LobbyManager.class);
    private final UserService userService = mock(UserService.class);
    private final GameService gameService = mock(GameService.class);
    private final GameAvailabilityService gameAvailabilityService = mock(GameAvailabilityService.class);
    private final ChatService chatService = mock(ChatService.class);
    private final UltrakillLevelService ultrakillLevelService = mock(UltrakillLevelService.class);
    private final NotificationService notificationService = mock(NotificationService.class);
    private final FriendService friendService = mock(FriendService.class);
    private final PointsService pointsService = mock(PointsService.class);
    private final LobbyEventPublisher eventPublisher = mock(LobbyEventPublisher.class);
    private final TaskScheduler taskScheduler = mock(TaskScheduler.class);

    private LobbyService lobbyService;

    @BeforeEach
    void setUp() {
        when(lobbyManager.getLobbies()).thenReturn(List.of());
        lobbyService = new LobbyService(
                lobbyManager,
                userService,
                gameService,
                gameAvailabilityService,
                chatService,
                ultrakillLevelService,
                notificationService,
                friendService,
                pointsService,
                eventPublisher,
                taskScheduler
        );
    }

    @Test
    void invitesActiveFriendToCurrentLobby() {
        var user = user(1L, "User");
        var friend = user(2L, "Friend");
        var lobby = lobby(user, UUID.randomUUID());
        cacheLobby(user, lobby);

        when(friendService.getActiveFriend(user, 2L)).thenReturn(friend);
        when(friendService.isBlocked(friend, user)).thenReturn(false);

        lobbyService.inviteFriendToLobby(user, 2L);

        verify(notificationService).createGameInviteNotification(user, friend, lobby.getId());
    }

    @Test
    void rejectsInviteWhenFriendBlockedUser() {
        var user = user(1L, "User");
        var friend = user(2L, "Friend");

        when(friendService.getActiveFriend(user, 2L)).thenReturn(friend);
        when(friendService.isBlocked(friend, user)).thenReturn(true);

        assertThatThrownBy(() -> lobbyService.inviteFriendToLobby(user, 2L))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);

        verifyNoInteractions(notificationService);
    }

    @Test
    void rejectsInviteWhenUserIsNotInLobby() {
        var user = user(1L, "User");
        var friend = user(2L, "Friend");

        when(friendService.getActiveFriend(user, 2L)).thenReturn(friend);
        when(friendService.isBlocked(friend, user)).thenReturn(false);

        assertThatThrownBy(() -> lobbyService.inviteFriendToLobby(user, 2L))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);

        verifyNoInteractions(notificationService);
    }

    @Test
    void propagatesMissingActiveFriend() {
        var user = user(1L, "User");
        var error = new ResponseStatusException(HttpStatus.NOT_FOUND, "Active friend relation not found");
        when(friendService.getActiveFriend(user, 2L)).thenThrow(error);

        assertThatThrownBy(() -> lobbyService.inviteFriendToLobby(user, 2L))
                .isSameAs(error);

        verifyNoInteractions(notificationService);
    }

    @Test
    void rejectsLobbyCreationWhenUserIsAlreadyInLobby() {
        var user = user(1L, "User");
        cacheLobby(user, lobby(user, UUID.randomUUID()));

        assertThatThrownBy(() -> lobbyService.createLobby(user, new GameLobbyDTO()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("You are already in a lobby")
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);

        verifyNoInteractions(ultrakillLevelService, chatService, eventPublisher, taskScheduler);
        verify(lobbyManager, never()).createLobby(any(GameLobbyDTO.class), any());
    }

    @Test
    void rejectsLobbyCreationForDisabledGameModes() {
        var user = user(1L, "User");
        var request = new GameLobbyDTO();
        request.setGameType(GameTypeDTO.Briskula);
        request.setGameConfig(new BriskulaGameConfigDTO(3, 3, false, null));
        var disabled = new ResponseStatusException(HttpStatus.CONFLICT, "Game availability is disabled for BRISKULA / THREE_PLAYERS");
        org.mockito.Mockito.doThrow(disabled).when(gameAvailabilityService).requireEnabled(
                request.getGameType(), request.getGameConfig());

        assertThatThrownBy(() -> lobbyService.createLobby(user, request)).isSameAs(disabled);

        verifyNoInteractions(chatService, eventPublisher, taskScheduler);
        verify(lobbyManager, never()).createLobby(any(GameLobbyDTO.class), any());
    }

    @Test
    void refusesAStakeTheOwnerCannotCover() {
        var owner = user(1L, "Owner");
        when(pointsService.balance(owner)).thenReturn(900L);
        var request = new GameLobbyDTO();
        request.setGameType(GameTypeDTO.Briskula);
        request.setGameConfig(new BriskulaGameConfigDTO(2, 3, false, null));
        request.setWager(new WagerConfigDTO(true, 1_000));

        assertThatThrownBy(() -> lobbyService.createLobby(owner, request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("900P")
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);

        verify(lobbyManager, never()).createLobby(any(GameLobbyDTO.class), any());
    }

    @Test
    void dropsPlayersWhoCannotCoverTheStakeWhenTheLobbyReopens() {
        var owner = user(1L, "Owner");
        var broke = user(2L, "Broke");
        var flush = user(3L, "Flush");
        var lobby = startedLobbyWithWager(owner, 1_000, broke, flush);
        when(pointsService.balance(owner)).thenReturn(5_000L);
        when(pointsService.balance(broke)).thenReturn(120L);
        when(pointsService.balance(flush)).thenReturn(1_000L);

        assertThat(lobbyService.openLobby(lobby)).isTrue();

        assertThat(lobby.getUsers()).containsExactlyInAnyOrder(owner, flush);
        verify(eventPublisher).publishKicked(broke, lobby.getId());
        verify(eventPublisher, never()).publishKicked(flush, lobby.getId());
    }

    @Test
    void closesTheLobbyWhenTheOwnerCannotCoverTheStake() {
        var owner = user(1L, "Owner");
        var player = user(2L, "Player");
        var lobby = startedLobbyWithWager(owner, 1_000, player);
        when(pointsService.balance(owner)).thenReturn(200L);
        when(lobbyManager.deleteLobby(lobby)).thenReturn(true);

        assertThat(lobbyService.openLobby(lobby)).isFalse();

        verify(lobbyManager).deleteLobby(lobby);
        verify(eventPublisher).publish(lobby, DELETED);
    }

    @Test
    void leavesAFreeLobbyAloneWhenItReopens() {
        var owner = user(1L, "Owner");
        var player = user(2L, "Player");
        var lobby = startedLobbyWithWager(owner, 0, player);

        assertThat(lobbyService.openLobby(lobby)).isTrue();

        assertThat(lobby.getUsers()).containsExactlyInAnyOrder(owner, player);
        verifyNoInteractions(pointsService);
        verify(lobbyManager, never()).deleteLobby(lobby);
    }

    private LobbyEntity startedLobbyWithWager(UserEntity owner, long stake, UserEntity... players) {
        var lobby = new LobbyEntity("Lobby", GameTypeDTO.Briskula, owner, 2, 4,
                new BriskulaGameConfigDTO(4, 3, false, null), LobbyState.PUBLIC, 60);
        for (var player : players) lobby.addUser(player);
        lobby.setWager(stake == 0 ? WagerConfigDTO.disabled() : new WagerConfigDTO(true, stake));
        lobby.setStarted(true);
        return lobby;
    }

    @Test
    void requiresPlayerKickBeforeReducingGameModePlayerCount() {
        var owner = user(1L, "Owner");
        var lobby = new LobbyEntity("Lobby", GameTypeDTO.Briskula, owner, 2, 3,
                new BriskulaGameConfigDTO(3, 3, false, null), LobbyState.PUBLIC, 60);
        lobby.addUser(user(2L, "Player 2"));
        lobby.addUser(user(3L, "Player 3"));
        when(lobbyManager.getLobby(lobby.getId())).thenReturn(lobby);

        var update = new GameLobbyDTO();
        update.setId(lobby.getId());
        update.setGameConfig(new BriskulaGameConfigDTO(2, 3, false, null));

        assertThatThrownBy(() -> lobbyService.updateLobby(update, owner))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Kick a player")
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);

        assertThat(lobby.getUsers()).hasSize(3).contains(owner);
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void deletesGameInviteNotificationsWhenLobbyIsDeleted() {
        var user = user(1L, "User");
        var lobbyId = UUID.randomUUID();
        var lobby = lobby(user, lobbyId);
        when(lobbyManager.deleteLobby(lobby)).thenReturn(true);

        lobbyService.deleteLobby(lobby);

        verify(notificationService).deleteGameInviteNotifications(lobbyId);
    }

    @Test
    void keepsGameInviteNotificationsWhenLobbyDeletionFails() {
        var user = user(1L, "User");
        var lobbyId = UUID.randomUUID();
        var lobby = lobby(user, lobbyId);
        when(lobbyManager.deleteLobby(lobby)).thenReturn(false);

        lobbyService.deleteLobby(lobby);

        verify(notificationService, never()).deleteGameInviteNotifications(lobbyId);
    }

    @Test
    void adminUpdateRejectsStartedLobby() {
        var lobbyId = UUID.randomUUID();
        var lobby = mock(LobbyEntity.class);
        when(lobbyManager.getLobby(lobbyId)).thenReturn(lobby);
        when(lobby.isStarted()).thenReturn(true);

        assertThatThrownBy(() -> lobbyService.updateLobby(lobbyId, "Renamed", null, null))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(error -> ((ResponseStatusException) error).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);

        verifyNoInteractions(eventPublisher);
    }

    @Test
    void adminKickCannotRemoveLobbyOwner() {
        var owner = user(1L, "Owner");
        var lobbyId = UUID.randomUUID();
        var lobby = mock(LobbyEntity.class);
        when(lobbyManager.getLobby(lobbyId)).thenReturn(lobby);
        when(lobby.getOwner()).thenReturn(owner);

        assertThatThrownBy(() -> lobbyService.kickPlayer(lobbyId, owner.getId()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("close the lobby")
                .extracting(error -> ((ResponseStatusException) error).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);

        verifyNoInteractions(userService, eventPublisher);
    }

    @Test
    void adminExtensionEnforcesOneMinuteToTwentyFourHours() {
        var lobbyId = UUID.randomUUID();
        var lobby = mock(LobbyEntity.class);
        when(lobbyManager.getLobby(lobbyId)).thenReturn(lobby);

        assertThatThrownBy(() -> lobbyService.extendLobby(lobbyId, 59))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(error -> ((ResponseStatusException) error).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThatThrownBy(() -> lobbyService.extendLobby(lobbyId, 86_401))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(error -> ((ResponseStatusException) error).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        verifyNoInteractions(taskScheduler, eventPublisher);
    }

    @Test
    void startsTresetaOnlyForOwnerWithExactConfiguredPlayerCount() {
        var owner = user(1L, "Owner");
        var player = user(2L, "Player");
        var lobby = mock(LobbyEntity.class);
        var config = mock(TresetaLobbyGameConfig.class);
        when(lobbyManager.getLobby(owner)).thenReturn(lobby);
        when(lobby.getOwner()).thenReturn(owner);
        when(lobby.getLobbyGameConfig()).thenReturn(config);
        when(config.getGameConfig()).thenReturn(TresetaGameConfig.TWO_PLAYERS);
        when(lobby.getUsers()).thenReturn(List.of(owner));

        assertThat(lobbyService.startLobby(owner)).isFalse();
        verifyNoInteractions(gameService);

        when(lobby.getUsers()).thenReturn(List.of(owner, player));
        assertThat(lobbyService.startLobby(owner)).isTrue();
        verify(gameService).startGame(lobby);
    }

    @Test
    void startsALobbyOnlyOnce() {
        var owner = user(1L, "Owner");
        var lobby = new LobbyEntity("Lobby", GameTypeDTO.Briskula, owner, 2, 2,
                new BriskulaGameConfigDTO(2, 3, false, null), LobbyState.PUBLIC, 60);
        lobby.addUser(user(2L, "Player"));
        when(lobbyManager.getLobby(owner)).thenReturn(lobby);

        assertThat(lobbyService.startLobby(owner)).isTrue();
        assertThat(lobbyService.startLobby(owner)).isFalse();

        verify(gameService, times(1)).startGame(lobby);
    }

    @Test
    void ownerCannotUpdateAStartedLobby() {
        var owner = user(1L, "Owner");
        var lobbyId = UUID.randomUUID();
        var lobby = mock(LobbyEntity.class);
        var update = new GameLobbyDTO();
        update.setId(lobbyId);
        when(lobbyManager.getLobby(lobbyId)).thenReturn(lobby);
        when(lobby.getOwner()).thenReturn(owner);
        when(lobby.isStarted()).thenReturn(true);

        assertThatThrownBy(() -> lobbyService.updateLobby(update, owner))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(error -> ((ResponseStatusException) error).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);

        verifyNoInteractions(gameAvailabilityService, eventPublisher);
    }

    @Test
    void doesNotLetLobbyMemberStartOwnersGame() {
        var owner = user(1L, "Owner");
        var member = user(2L, "Member");
        var lobby = mock(LobbyEntity.class);
        when(lobbyManager.getLobby(member)).thenReturn(lobby);
        when(lobby.getOwner()).thenReturn(owner);

        assertThat(lobbyService.startLobby(member)).isFalse();
        verifyNoInteractions(gameService);
    }

    /** Membership is read straight off the live lobbies, so that is what the test stubs. */
    private void cacheLobby(UserEntity user, LobbyEntity lobby) {
        when(lobby.containsUser(user)).thenReturn(true);
        when(lobbyManager.getLobbies()).thenReturn(List.of(lobby));
    }

    private LobbyEntity lobby(UserEntity user, UUID lobbyId) {
        var lobby = mock(LobbyEntity.class);
        when(lobby.getId()).thenReturn(lobbyId);
        when(lobby.containsUser(user)).thenReturn(true);
        return lobby;
    }

    private UserEntity user(Long id, String username) {
        var user = new UserEntity(username + "@example.com", username);
        user.setId(id);
        return user;
    }
}
