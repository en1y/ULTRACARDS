package com.ultracards.gateway.service;

import com.ultracards.gateway.dto.notifications.NotificationDTO;
import org.springframework.messaging.simp.stomp.StompSession;

import java.util.function.Consumer;

public class NotificationWsService extends StompGatewayService {

    public NotificationWsService(String wsUrl, ClientTokenHolder tokenHolder) {
        super(wsUrl, tokenHolder);
    }

    public NotificationWsService(String wsUrl, ClientTokenHolder tokenHolder, TokenManager tokenManager) {
        super(wsUrl, tokenHolder, tokenManager);
    }

    public StompSession.Subscription subscribeToNotifications(Consumer<NotificationDTO> handler) {
        return subscribe("/user/queue/notifications", NotificationDTO.class, handler);
    }
}
