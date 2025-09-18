package com.ultracards.server.service.games;

import com.ultracards.gateway.dto.games.LobbyDTO;
import com.ultracards.gateway.dto.games.LobbyEventDTO;
import com.ultracards.gateway.dto.games.LobbyPlayerDTO;
import com.ultracards.server.entity.UserEntity;
import com.ultracards.server.entity.games.GameLobby;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
public class LobbyEventPublisher {
    private final SimpMessagingTemplate messagingTemplate;

    public LobbyEventPublisher(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    public void publish(String type, GameLobby lobby) {
        publish(type, lobby, null);
    }

    public void publish(String type, GameLobby lobby, java.util.UUID gameId) {
        var dto = toDto(lobby);
        messagingTemplate.convertAndSend("/topic/lobbies", new LobbyEventDTO(type, dto, gameId));
    }

    private LobbyDTO toDto(GameLobby lobby) {
        return new LobbyDTO(
                lobby.getId(),
                lobby.getLobbyName(),
                lobby.getGameType() != null ? lobby.getGameType().name() : null,
                lobby.getCreatedAt() != null ? lobby.getCreatedAt() : Instant.now(),
                lobby.getOwner() != null ? lobby.getOwner().getId() : null,
                lobby.getOwner() != null ? lobby.getOwner().getUsername() : null,
                lobby.getPlayers() != null ? lobby.getPlayers().stream().map(UserEntity::getId).toList() : List.of(),
                lobby.getPlayers() != null ? lobby.getPlayers().stream()
                        .map(u -> new LobbyPlayerDTO(u.getId(), u.getUsername()))
                        .toList() : List.of(),
                lobby.getMaxPlayers(),
                lobby.getConfigJson()
        );
    }
}
