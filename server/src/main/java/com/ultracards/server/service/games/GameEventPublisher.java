package com.ultracards.server.service.games;

import com.ultracards.gateway.dto.games.GameEventDTO;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
public class GameEventPublisher {
    private final SimpMessagingTemplate messagingTemplate;

    public GameEventPublisher(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    public void publish(UUID gameId, String eventType, String payloadJson) {
        var event = new GameEventDTO(gameId, eventType, payloadJson, Instant.now());
        messagingTemplate.convertAndSend("/topic/games/" + gameId + "/events", event);
    }
}

