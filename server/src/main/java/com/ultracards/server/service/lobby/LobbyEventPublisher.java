package com.ultracards.server.service.lobby;

import com.ultracards.gateway.dto.games.lobby.GameLobbyDTO;
import com.ultracards.gateway.dto.games.lobby.GameLobbyEventDTO;
import com.ultracards.server.entity.UserEntity;
import com.ultracards.server.entity.lobby.LobbyEntity;
import com.ultracards.server.service.games.GameManager;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;

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
        var gameId = type.equals(STARTED) ? gameManager.getGameByLobbyId(lobbyDTO.getId()).getId() : null;
        var privateEvent = new GameLobbyEventDTO(type, lobby.createLobbyDTO(true), gameId);
        messagingTemplate.convertAndSend("/topic/lobbies", publicEvent);
        if (!type.equals(CREATED)) {
            for (var user : lobby.getUsers()) {
                messagingTemplate.convertAndSendToUser(
                        user.getId().toString(), "/queue/lobby", privateEvent);
            }
        }
    }

    public void publishKicked(UserEntity user, UUID lobbyId) {
        var lobby = new GameLobbyDTO();
        lobby.setId(lobbyId);
        messagingTemplate.convertAndSendToUser(
                user.getId().toString(),
                "/queue/lobby",
                new GameLobbyEventDTO(GameLobbyEventType.KICKED, lobby)
        );
    }
}
