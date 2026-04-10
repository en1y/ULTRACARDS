package com.ultracards.server.service.chat;

import com.ultracards.server.entity.UserEntity;
import com.ultracards.server.entity.chat.ChatEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ChatService{
    private final ChatManager chatManager;
    private final ChatEventPublisher eventPublisher;

    public ChatEntity getChat(UUID lobbyId) {
        return chatManager.getChat(lobbyId);
    }

    public void sendMessage(UUID lobbyId, UserEntity sender, String message) {
        var messageObj = getChat(lobbyId).sendMessage(sender, message);
        eventPublisher.publish(messageObj.toDto(), lobbyId);
    }

    public void deleteChat(UUID lobbyId) {
        chatManager.deleteChat(lobbyId);
    }

    public void createChat(UUID lobbyId) {
        chatManager.createChat(lobbyId);
    }
}
