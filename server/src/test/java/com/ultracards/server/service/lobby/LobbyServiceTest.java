package com.ultracards.server.service.lobby;

import com.ultracards.server.entity.UserEntity;
import com.ultracards.server.entity.lobby.LobbyEntity;
import com.ultracards.server.entity.lobby.TresetaLobbyGameConfig;
import com.ultracards.games.treseta.TresetaGameConfig;
import com.ultracards.server.service.chat.ChatService;
import com.ultracards.server.service.friends.FriendService;
import com.ultracards.server.service.games.GameService;
import com.ultracards.server.service.notifications.NotificationService;
import com.ultracards.server.service.ultrakill.UltrakillLevelService;
import com.ultracards.server.service.users.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class LobbyServiceTest {

    private final LobbyManager lobbyManager = mock(LobbyManager.class);
    private final UserService userService = mock(UserService.class);
    private final GameService gameService = mock(GameService.class);
    private final ChatService chatService = mock(ChatService.class);
    private final UltrakillLevelService ultrakillLevelService = mock(UltrakillLevelService.class);
    private final NotificationService notificationService = mock(NotificationService.class);
    private final FriendService friendService = mock(FriendService.class);
    private final LobbyEventPublisher eventPublisher = mock(LobbyEventPublisher.class);
    private final TaskScheduler taskScheduler = mock(TaskScheduler.class);

    private LobbyService lobbyService;

    @BeforeEach
    void setUp() {
        lobbyService = new LobbyService(
                lobbyManager,
                userService,
                gameService,
                chatService,
                ultrakillLevelService,
                notificationService,
                friendService,
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
    void doesNotLetLobbyMemberStartOwnersGame() {
        var owner = user(1L, "Owner");
        var member = user(2L, "Member");
        var lobby = mock(LobbyEntity.class);
        when(lobbyManager.getLobby(member)).thenReturn(lobby);
        when(lobby.getOwner()).thenReturn(owner);

        assertThat(lobbyService.startLobby(member)).isFalse();
        verifyNoInteractions(gameService);
    }

    private void cacheLobby(UserEntity user, LobbyEntity lobby) {
        var lobbyCache = (HashMap<Long, LobbyEntity>) ReflectionTestUtils.getField(lobbyService, "lobbyCache");
        lobbyCache.put(user.getId(), lobby);
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
