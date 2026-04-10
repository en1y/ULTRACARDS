package com.ultracards.server.service.chat;

import com.ultracards.gateway.dto.games.chat.ChatMessageDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ChatEventPublisher {
    private final SimpMessagingTemplate messagingTemplate;

    public void publish(ChatMessageDTO message, UUID lobbyId) {
        messagingTemplate.convertAndSend("/topic/lobbies/" + lobbyId + "/chat", message);
    }
}
