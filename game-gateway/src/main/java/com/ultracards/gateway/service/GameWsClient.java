package com.ultracards.gateway.service;

import com.ultracards.gateway.dto.games.GameEventDTO;
import com.ultracards.gateway.dto.games.HeartbeatDTO;
import com.ultracards.gateway.dto.games.PlayerActionDTO;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.reflect.Type;
import java.net.URI;
import java.time.Instant;
import java.util.UUID;
import java.util.function.Consumer;

public class GameWsClient {

    private StompSession session;
    private final WebSocketStompClient stompClient;
    private final String wsUrl; // e.g., ws://host:port/ws

    public GameWsClient(String wsUrl) {
        this.wsUrl = wsUrl;
        this.stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        var scheduler = new ThreadPoolTaskScheduler();
        scheduler.initialize();
        this.stompClient.setTaskScheduler(scheduler);
        this.stompClient.setMessageConverter(new MappingJackson2MessageConverter());
    }

    public void connect(ClientTokenHolder tokenHolder, Runnable onConnected, Consumer<Throwable> onError) {
        var headers = new WebSocketHttpHeaders();
        if (tokenHolder != null && tokenHolder.getToken() != null) {
            headers.add("Cookie", "refreshToken=" + tokenHolder.getToken());
        }
        stompClient.connectAsync(URI.create(wsUrl).toString(), headers, new StompSessionHandlerAdapter() {
            @Override
            public void afterConnected(StompSession s, StompHeaders connectedHeaders) {
                session = s;
                if (onConnected != null) onConnected.run();
            }

            @Override
            public void handleTransportError(StompSession s, Throwable ex) {
                if (onError != null) onError.accept(ex);
            }
        });
    }

    public StompSession.Subscription subscribeToGame(UUID gameId, Consumer<GameEventDTO> handler) {
        return session.subscribe("/topic/games/" + gameId + "/events", new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return GameEventDTO.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                handler.accept((GameEventDTO) payload);
            }
        });
    }

    public void sendHeartbeat(UUID gameId) {
        var hb = new HeartbeatDTO(gameId, Instant.now(), null);
        session.send("/app/games/" + gameId + "/heartbeat", hb);
    }

    public void sendAction(PlayerActionDTO action) {
        session.send("/app/games/" + action.getGameId() + "/action", action);
    }

    public void sendLobbyHeartbeat(UUID lobbyId, Long userId) {
        var hb = new HeartbeatDTO(null, Instant.now(), userId);
        session.send("/app/lobbies/" + lobbyId + "/heartbeat", hb);
    }

    public void disconnect() {
        if (session != null && session.isConnected()) session.disconnect();
    }
}
