package com.ultracards.gateway.service;

import com.ultracards.gateway.dto.updated.games.games.GameEventDTO;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.messaging.simp.stomp.StompSession.Subscription;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.reflect.Type;
import java.net.URI;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * WebSocket client for game-specific events. Clients can subscribe to `/topic/lobbies/{gameId}`.
 */
public class GameWsService {

    private final WebSocketStompClient stompClient;
    private final String wsUrl;
    private final ClientTokenHolder tokenHolder;
    private final TokenManager tokenManager;
    private StompSession session;

    public GameWsService(String wsUrl, ClientTokenHolder tokenHolder) {
        this(wsUrl, tokenHolder, new TokenManager(tokenHolder));
    }

    public GameWsService(String wsUrl, ClientTokenHolder tokenHolder, TokenManager tokenManager) {
        this.wsUrl = wsUrl;
        this.tokenHolder = tokenHolder;
        this.tokenManager = tokenManager;
        this.stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        var scheduler = new ThreadPoolTaskScheduler();
        scheduler.initialize();
        this.stompClient.setTaskScheduler(scheduler);
        this.stompClient.setMessageConverter(new MappingJackson2MessageConverter());
    }

    /**
     * Establishes a STOMP session with cookie-based authentication.
     */
    public void connect(Runnable onConnected, Consumer<Throwable> onError) {
        var headers = new WebSocketHttpHeaders();
        var token = tokenManager != null ? tokenManager.tokenValue(tokenHolder) :
                (tokenHolder != null ? tokenHolder.getToken() : null);
        if (token != null) {
            headers.add("Cookie", "refreshToken=" + token);
        }
        stompClient.connectAsync(URI.create(wsUrl).toString(), headers, new StompSessionHandlerAdapter() {
            @Override
            public void afterConnected(StompSession stompSession, StompHeaders connectedHeaders) {
                session = stompSession;
                if (onConnected != null) {
                    onConnected.run();
                }
            }

            @Override
            public void handleTransportError(StompSession stompSession, Throwable ex) {
                if (onError != null) {
                    onError.accept(ex);
                }
            }
        });
    }

    public Subscription subscribeToGame(UUID gameId, Consumer<GameEventDTO> handler) {
        ensureSession();
        return subscribeDestination("/topic/lobbies/" + gameId, handler);
    }

    public void disconnect() {
        if (session != null && session.isConnected()) {
            session.disconnect();
        }
    }

    private Subscription subscribeDestination(String destination, Consumer<GameEventDTO> handler) {
        return session.subscribe(destination, new StompFrameHandler() {
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

    private void ensureSession() {
        if (session == null || !session.isConnected()) {
            throw new IllegalStateException("WebSocket session is not connected; call connect() first.");
        }
    }
}
