package com.ultracards.server.service.chat;

import com.ultracards.server.entity.chat.ChatEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ChatManager {
    private final Map<UUID, ChatEntity> chatsByLobbyId = new HashMap<>();

    public ChatEntity getChat(UUID lobbyId) {
        return chatsByLobbyId.get(lobbyId);
    }

    public ChatEntity createChat(UUID lobbyId) {
        var chat = new ChatEntity(lobbyId);
        chatsByLobbyId.put(lobbyId, chat);
        return chat;
    }

    public void deleteChat(UUID lobbyId) {
        chatsByLobbyId.remove(lobbyId);
    }
}
