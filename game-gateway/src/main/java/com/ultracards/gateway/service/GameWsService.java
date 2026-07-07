package com.ultracards.gateway.service;

import com.ultracards.gateway.dto.games.games.GameCardDTO;
import com.ultracards.gateway.dto.games.games.GameEventDTO;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.messaging.simp.stomp.StompSession;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

public class GameWsService extends StompGatewayService {

    public GameWsService(String wsUrl, ClientTokenHolder tokenHolder) {
        super(wsUrl, tokenHolder);
    }

    public GameWsService(String wsUrl, ClientTokenHolder tokenHolder, TokenManager tokenManager) {
        super(wsUrl, tokenHolder, tokenManager);
    }

    public void playCard(GameCardDTO card) {
        send("/app/game/play", card);
    }

    public StompSession.Subscription subscribeToGame(UUID gameId, Consumer<GameEventDTO> handler) {
        return subscribe("/topic/game/" + gameId, GameEventDTO.class, handler);
    }

    public StompSession.Subscription subscribeToCards(Consumer<List<GameCardDTO>> handler) {
        return subscribe("/user/queue/game/cards", new ParameterizedTypeReference<List<GameCardDTO>>() {}, handler);
    }

    public StompSession.Subscription subscribeToTeammateCards(Consumer<List<GameCardDTO>> handler) {
        return subscribe("/user/queue/game/teammate-cards", new ParameterizedTypeReference<List<GameCardDTO>>() {}, handler);
    }
}
