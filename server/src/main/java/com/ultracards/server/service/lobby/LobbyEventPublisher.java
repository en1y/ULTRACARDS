package com.ultracards.server.service.lobby;

import com.ultracards.gateway.dto.games.lobby.GameLobbyDTO;
import com.ultracards.gateway.dto.games.lobby.GameLobbyEventDTO;
import com.ultracards.server.entity.lobby.LobbyEntity;
import com.ultracards.server.service.games.GameManager;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

import static com.ultracards.gateway.dto.games.lobby.GameLobbyEventDTO.GameLobbyEventType;
import static com.ultracards.gateway.dto.games.lobby.GameLobbyEventDTO.GameLobbyEventType.*;

@Service
@RequiredArgsConstructor
public class LobbyEventPublisher {
    private final SimpMessagingTemplate messagingTemplate;
    private final GameManager gameManager;

    public void publish(LobbyEntity lobby, GameLobbyEventType type) {
        var lobbyDTO = lobby.createLobbyDTO(false);
        var publicEvent = new GameLobbyEventDTO(type, lobbyDTO);
        var privateEvent = new GameLobbyEventDTO(type, lobby.createLobbyDTO(true));
        messagingTemplate.convertAndSend("/topic/lobbies", publicEvent);
        if (!type.equals(CREATED) && !type.equals(STARTED)) {
            messagingTemplate.convertAndSend(
                    "/topic/lobbies/" + lobbyDTO.getId(), privateEvent);
        }
        if (type.equals(STARTED)) {
            var game = gameManager.getGameByLobbyId(lobbyDTO.getId());
            messagingTemplate.convertAndSend(
                    "/topic/lobbies/" + lobbyDTO.getId(), privateEvent);
            messagingTemplate.convertAndSend(
                    "/topic/lobbies/" + lobbyDTO.getId(), (Object) Map.of("gameId", game.getId()));
        }
    }
}
