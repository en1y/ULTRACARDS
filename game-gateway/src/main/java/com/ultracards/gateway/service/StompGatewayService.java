package com.ultracards.gateway.service;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.messaging.converter.JacksonJsonMessageConverter;
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
import java.util.function.Consumer;

public abstract class StompGatewayService implements AutoCloseable {
    private final WebSocketStompClient stompClient;
    private final ThreadPoolTaskScheduler scheduler;
    private final String wsUrl;
    private final ClientTokenHolder tokenHolder;
    private final TokenManager tokenManager;
    private StompSession session;

    StompGatewayService(String wsUrl, ClientTokenHolder tokenHolder) {
        this(wsUrl, tokenHolder, new TokenManager(tokenHolder));
    }

    StompGatewayService(String wsUrl, ClientTokenHolder tokenHolder, TokenManager tokenManager) {
        this.wsUrl = wsUrl;
        this.tokenHolder = tokenHolder;
        this.tokenManager = tokenManager;
        this.scheduler = new ThreadPoolTaskScheduler();
        this.scheduler.initialize();
        this.stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        this.stompClient.setTaskScheduler(scheduler);
        this.stompClient.setMessageConverter(new JacksonJsonMessageConverter());
    }

    public void connect(Runnable onConnected, Consumer<Throwable> onError) {
        var headers = new WebSocketHttpHeaders();
        var token = tokenManager.tokenValue(tokenHolder);
        if (token != null) {
            headers.add("Cookie", "refreshToken=" + token);
        }

        stompClient.connectAsync(URI.create(wsUrl).toString(), headers, new StompSessionHandlerAdapter() {
            @Override
            public void afterConnected(StompSession stompSession, StompHeaders connectedHeaders) {
                session = stompSession;
                if (onConnected != null) onConnected.run();
            }

            @Override
            public void handleTransportError(StompSession stompSession, Throwable ex) {
                if (onError != null) onError.accept(ex);
            }
        });
    }

    public void disconnect() {
        if (session != null && session.isConnected()) {
            session.disconnect();
        }
        scheduler.shutdown();
    }

    @Override
    public void close() {
        disconnect();
    }

    protected void send(String destination, Object payload) {
        ensureSession();
        session.send(destination, payload);
    }

    protected <T> StompSession.Subscription subscribe(
            String destination,
            Class<T> payloadType,
            Consumer<T> handler
    ) {
        return subscribe(destination, (Type) payloadType, handler);
    }

    protected <T> StompSession.Subscription subscribe(
            String destination,
            ParameterizedTypeReference<T> payloadType,
            Consumer<T> handler
    ) {
        return subscribe(destination, payloadType.getType(), handler);
    }

    private <T> StompSession.Subscription subscribe(
            String destination,
            Type payloadType,
            Consumer<T> handler
    ) {
        ensureSession();
        return session.subscribe(destination, new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return payloadType;
            }

            @Override
            @SuppressWarnings("unchecked")
            public void handleFrame(StompHeaders headers, Object payload) {
                handler.accept((T) payload);
            }
        });
    }

    private void ensureSession() {
        if (session == null || !session.isConnected()) {
            throw new IllegalStateException("WebSocket session is not connected; call connect() first.");
        }
    }
}
