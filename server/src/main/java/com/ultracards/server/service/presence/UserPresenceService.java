package com.ultracards.server.service.presence;

import com.ultracards.gateway.dto.friends.UserPresenceStatusDTO;
import com.ultracards.server.entity.UserEntity;
import com.ultracards.server.entity.lobby.LobbyState;
import com.ultracards.server.repositories.auth.SessionRepository;
import com.ultracards.server.service.games.GameManager;
import com.ultracards.server.service.lobby.LobbyManager;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class UserPresenceService {
    private final GameManager gameManager;
    private final LobbyManager lobbyManager;
    private final SessionRepository sessionRepository;

    @Value("${app.presence.online-timeout-seconds:60}")
    private long onlineTimeoutSeconds;

    public UserPresenceStatusDTO getStatus(UserEntity user) {
        var game = gameManager.getGame(user.getId());
        if (game != null && game.isActive()) {
            return UserPresenceStatusDTO.IN_GAME;
        }

        for (var lobby : lobbyManager.getLobbies()) {
            if (!lobby.isStarted()
                    && !lobby.getLobbyState().equals(LobbyState.CLOSED)
                    && lobby.containsUser(user)) {
                return UserPresenceStatusDTO.IN_LOBBY;
            }
        }

        var onlineThreshold = Instant.now().minusSeconds(onlineTimeoutSeconds);
        if (sessionRepository.existsByUserIdAndLastSeenAtAfter(user.getId(), onlineThreshold)) {
            return UserPresenceStatusDTO.ONLINE;
        }

        return UserPresenceStatusDTO.OFFLINE;
    }
}
