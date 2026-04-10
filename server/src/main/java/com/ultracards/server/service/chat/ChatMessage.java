package com.ultracards.server.service.chat;

import com.ultracards.gateway.dto.games.GamePlayerDTO;
import com.ultracards.gateway.dto.games.chat.ChatMessageDTO;
import com.ultracards.server.entity.UserEntity;
import lombok.Data;

import java.time.Instant;

@Data
public class ChatMessage {
    private UserEntity user;
    private String message;
    private Instant timestamp = Instant.now();

    public ChatMessage(UserEntity user, String message) {
        this.user = user;
        this.message = message;
    }

    public ChatMessageDTO toDto() {
        return new ChatMessageDTO(
                new GamePlayerDTO(user.getUsername(), user.getId()),
                message,
                timestamp
        );
    }
}
