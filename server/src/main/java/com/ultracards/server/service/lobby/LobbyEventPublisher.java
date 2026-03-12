package com.ultracards.server.service.lobby;

import com.ultracards.gateway.dto.games.lobby.GameLobbyDTO;
import com.ultracards.gateway.dto.games.lobby.GameLobbyEventDTO;
import com.ultracards.server.entity.lobby.LobbyEntity;
import com.ultracards.server.service.games.GameManager;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;

import static com.ultracards.gateway.dto.games.lobby.GameLobbyEventDTO.GameLobbyEventType;
import static com.ultracards.gateway.dto.games.lobby.GameLobbyEventDTO.GameLobbyEventType.*;

@Service
@RequiredArgsConstructor
public class LobbyEventPublisher {
    private final SimpMessagingTemplate messagingTemplate;
    private final GameManager gameManager;

    public void publish(LobbyEntity lobby, GameLobbyEventType type) {
        publish(lobby.createLobbyDTO(), type);
    }

    public void publish(GameLobbyDTO lobbyDTO, GameLobbyEventType type) {
        var event = new GameLobbyEventDTO(type, lobbyDTO);
        messagingTemplate.convertAndSend("/topic/lobbies", event);
        if (!type.equals(CREATED) && !type.equals(STARTED)) {
            messagingTemplate.convertAndSend(
                    "/topic/lobbies/" + lobbyDTO.getId(), event);
        }
        if (type.equals(STARTED)) {
            var game = gameManager.getGameByLobbyId(lobbyDTO.getId());
            messagingTemplate.convertAndSend(
                    "/topic/lobbies/" + lobbyDTO.getId(), event);
            messagingTemplate.convertAndSend(
                    "/topic/lobbies/" + lobbyDTO.getId(), Map.of("gameId", game.getId()));
        }
    }
}
