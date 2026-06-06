package com.ultracards.server.service.notifications;

import com.ultracards.gateway.dto.games.GamePlayerDTO;
import com.ultracards.gateway.dto.notifications.NotificationDTO;
import com.ultracards.gateway.dto.notifications.NotificationTypeDTO;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class NotificationEventPublisherTest {

    private final SimpMessagingTemplate messagingTemplate = mock(SimpMessagingTemplate.class);
    private final NotificationEventPublisher publisher = new NotificationEventPublisher(messagingTemplate);

    @Test
    void publishesNotificationToRecipientQueue() {
        var notification = new NotificationDTO(
                UUID.randomUUID(),
                NotificationTypeDTO.TEXT,
                "Hello",
                null,
                null,
                new GamePlayerDTO("Recipient", 2L),
                false,
                Instant.parse("2026-06-04T10:00:00Z"),
                null
        );

        publisher.publish(notification);

        verify(messagingTemplate).convertAndSendToUser(
                eq("2"),
                eq("/queue/notifications"),
                same(notification)
        );
    }
}
