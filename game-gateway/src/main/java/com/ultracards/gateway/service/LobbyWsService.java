package com.ultracards.gateway.service;

import com.ultracards.gateway.dto.updated.games.lobby.GameLobbyEventDTO;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.reflect.Type;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * STOMP client for lobby lifecycle updates (CREATED, UPDATED, DELETED, STARTED).
 * Connect once, then subscribe to lobby topics to receive {@link GameLobbyEventDTO} notifications.
 */
public class LobbyWsService {

    private final WebSocketStompClient stompClient;
    private final String wsUrl; // e.g., ws://host:port/ws
    private final ClientTokenHolder tokenHolder;
    private final TokenManager tokenManager;
    private StompSession session;

    public LobbyWsService(String wsUrl, ClientTokenHolder tokenHolder) {
        this(wsUrl, tokenHolder, new TokenManager(tokenHolder));
    }

    public LobbyWsService(String wsUrl, ClientTokenHolder tokenHolder, TokenManager tokenManager) {
        this.wsUrl = wsUrl;
        this.tokenHolder = tokenHolder;
        this.tokenManager = tokenManager;
        this.stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        var scheduler = new ThreadPoolTaskScheduler();
        scheduler.initialize();
        this.stompClient.setTaskScheduler(scheduler);
        this.stompClient.setMessageConverter(new MappingJackson2MessageConverter());
    }

    public void connect(Runnable onConnected, Consumer<Throwable> onError) {
        var headers = new WebSocketHttpHeaders();
        var token = tokenManager != null ? tokenManager.tokenValue(tokenHolder) :
                (tokenHolder != null ? tokenHolder.getToken() : null);
        if (token != null) {
            headers.add("Cookie", "refreshToken=" + token);
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

    /**
     * Subscribe to global lobby events published to /topic/lobbies.
     */
    public List<StompSession.Subscription> subscribeToLobbies(Consumer<GameLobbyEventDTO> handler) {
        ensureSession();
        var subs = new ArrayList<StompSession.Subscription>(1);
        subs.add(subscribeDestination("/topic/lobbies", handler));
        return subs;
    }

    /**
     * Subscribe to a specific lobby channel (e.g., updates scoped to a lobby id).
     */
    public StompSession.Subscription subscribeToLobby(UUID lobbyId, Consumer<GameLobbyEventDTO> handler) {
        ensureSession();
        return subscribeDestination("/topic/lobbies/" + lobbyId, handler);
    }

    private StompSession.Subscription subscribeDestination(String destination, Consumer<GameLobbyEventDTO> handler) {
        return session.subscribe(destination, new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return GameLobbyEventDTO.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                handler.accept((GameLobbyEventDTO) payload);
            }
        });
    }

    private void ensureSession() {
        if (session == null || !session.isConnected()) {
            throw new IllegalStateException("WebSocket session is not connected; call connect() first.");
        }
    }

    public void disconnect() {
        if (session != null && session.isConnected()) {
            session.disconnect();
        }
    }
}
