package com.ultracards.server.service.presence;

import com.ultracards.gateway.dto.friends.UserPresenceStatusDTO;
import com.ultracards.server.entity.UserEntity;
import com.ultracards.server.entity.games.GameEntity;
import com.ultracards.server.entity.lobby.LobbyEntity;
import com.ultracards.server.entity.lobby.LobbyState;
import com.ultracards.server.repositories.auth.SessionRepository;
import com.ultracards.server.service.games.GameManager;
import com.ultracards.server.service.lobby.LobbyManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UserPresenceServiceTest {
    private final GameManager gameManager = mock(GameManager.class);
    private final LobbyManager lobbyManager = mock(LobbyManager.class);
    private final SessionRepository sessionRepository = mock(SessionRepository.class);
    private final UserPresenceService userPresenceService = new UserPresenceService(
            gameManager,
            lobbyManager,
            sessionRepository
    );

    private final UserEntity user = user(1L, "User");

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(userPresenceService, "onlineTimeoutSeconds", 60L);
    }

    @Test
    void returnsInGameBeforeOtherStates() {
        var game = mock(GameEntity.class);

        when(gameManager.getGame(1L)).thenReturn(game);
        when(game.isActive()).thenReturn(true);

        var status = userPresenceService.getStatus(user);

        assertThat(status).isEqualTo(UserPresenceStatusDTO.IN_GAME);
    }

    @Test
    void returnsInLobbyWhenUserIsInOpenLobby() {
        var lobby = mock(LobbyEntity.class);

        when(lobbyManager.getLobbies()).thenReturn(List.of(lobby));
        when(lobby.isStarted()).thenReturn(false);
        when(lobby.getLobbyState()).thenReturn(LobbyState.PUBLIC);
        when(lobby.containsUser(user)).thenReturn(true);

        var status = userPresenceService.getStatus(user);

        assertThat(status).isEqualTo(UserPresenceStatusDTO.IN_LOBBY);
    }

    @Test
    void returnsOnlineWhenUserHasRecentSession() {
        when(lobbyManager.getLobbies()).thenReturn(List.of());
        when(sessionRepository.existsByUserIdAndLastSeenAtAfter(eq(1L), any(Instant.class))).thenReturn(true);

        var status = userPresenceService.getStatus(user);

        assertThat(status).isEqualTo(UserPresenceStatusDTO.ONLINE);
    }

    @Test
    void returnsOfflineWithoutActiveGameLobbyOrRecentSession() {
        when(lobbyManager.getLobbies()).thenReturn(List.of());

        var status = userPresenceService.getStatus(user);

        assertThat(status).isEqualTo(UserPresenceStatusDTO.OFFLINE);
    }

    private UserEntity user(Long id, String username) {
        var user = new UserEntity(username + "@example.com", username);
        user.setId(id);
        return user;
    }
}
