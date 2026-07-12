package com.ultracards.gateway.service;

import com.ultracards.gateway.dto.games.lobby.GameLobbyEventDTO;
import org.springframework.messaging.simp.stomp.StompSession;

import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

public class LobbyWsService extends StompGatewayService {

    public LobbyWsService(String wsUrl, ClientTokenHolder tokenHolder) {
        super(wsUrl, tokenHolder);
    }

    public LobbyWsService(String wsUrl, ClientTokenHolder tokenHolder, TokenManager tokenManager) {
        super(wsUrl, tokenHolder, tokenManager);
    }

    public StompSession.Subscription subscribeToLobbies(Consumer<GameLobbyEventDTO> handler) {
        return subscribe("/topic/lobbies", GameLobbyEventDTO.class, handler);
    }

    public StompSession.Subscription subscribeToLobby(UUID lobbyId, Consumer<GameLobbyEventDTO> handler) {
        return subscribe("/topic/lobbies/" + lobbyId, GameLobbyEventDTO.class, handler);
    }

    @Deprecated(forRemoval = true)
    public StompSession.Subscription subscribeToLobbyGameId(UUID lobbyId, Consumer<Map> handler) {
        return subscribeToLobby(lobbyId, event -> {
            if (event.getGameId() != null) handler.accept(Map.of("gameId", event.getGameId()));
        });
    }
}
