package com.ultracards.server.service.notifications;

import com.ultracards.gateway.dto.notifications.NotificationDTO;
import com.ultracards.server.entity.notifications.NotificationEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class NotificationEventPublisher {

    private final SimpMessagingTemplate messagingTemplate;

    public void publish(NotificationEntity notification) {
        publish(notification.toDto());
    }

    public void publish(NotificationDTO notification) {
        messagingTemplate.convertAndSendToUser(
                notification.getRecipient().getId().toString(),
                "/queue/notifications",
                notification
        );
    }
}
