package com.ultracards.gateway.service;

import com.ultracards.gateway.dto.games.games.GameCardDTO;
import com.ultracards.gateway.dto.games.games.GameEventDTO;
import com.ultracards.gateway.dto.games.games.GameEntityDTO;
import com.ultracards.gateway.dto.games.games.briskula.BriskulaGameEntityDTO;
import com.ultracards.gateway.dto.games.games.treseta.TresetaGameEntityDTO;
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

    /**
     * Subscribe to game-agnostic fields only. Prefer a typed game subscription when rendering a game screen.
     */
    public StompSession.Subscription subscribeToGame(UUID gameId, Consumer<GameEventDTO<GameEntityDTO>> handler) {
        return subscribe("/topic/game/" + gameId,
                new ParameterizedTypeReference<GameEventDTO<GameEntityDTO>>() {}, handler);
    }

    public StompSession.Subscription subscribeToBriskulaGame(
            UUID gameId,
            Consumer<GameEventDTO<BriskulaGameEntityDTO>> handler
    ) {
        return subscribe("/topic/game/" + gameId,
                new ParameterizedTypeReference<GameEventDTO<BriskulaGameEntityDTO>>() {}, handler);
    }

    public StompSession.Subscription subscribeToTresetaGame(
            UUID gameId,
            Consumer<GameEventDTO<TresetaGameEntityDTO>> handler
    ) {
        return subscribe("/topic/game/" + gameId,
                new ParameterizedTypeReference<GameEventDTO<TresetaGameEntityDTO>>() {}, handler);
    }

    public StompSession.Subscription subscribeToCards(Consumer<List<GameCardDTO>> handler) {
        return subscribe("/user/queue/game/cards", new ParameterizedTypeReference<List<GameCardDTO>>() {}, handler);
    }

    public StompSession.Subscription subscribeToTeammateCards(Consumer<List<GameCardDTO>> handler) {
        return subscribe("/user/queue/game/teammate-cards", new ParameterizedTypeReference<List<GameCardDTO>>() {}, handler);
    }

    public StompSession.Subscription subscribeToOpponentDrawnCards(Consumer<List<GameCardDTO>> handler) {
        return subscribe("/user/queue/game/opponent-drawn-cards", new ParameterizedTypeReference<List<GameCardDTO>>() {}, handler);
    }
}
