package com.ultracards.gateway.service;

import com.ultracards.gateway.dto.games.chat.ChatMessageDTO;
import org.springframework.messaging.simp.stomp.StompSession;

import java.util.UUID;
import java.util.function.Consumer;

public class ChatWsService extends StompGatewayService {

    public ChatWsService(String wsUrl, ClientTokenHolder tokenHolder) {
        super(wsUrl, tokenHolder);
    }

    public ChatWsService(String wsUrl, ClientTokenHolder tokenHolder, TokenManager tokenManager) {
        super(wsUrl, tokenHolder, tokenManager);
    }

    public StompSession.Subscription subscribeToLobbyChat(UUID lobbyId, Consumer<ChatMessageDTO> handler) {
        return subscribe("/topic/lobbies/" + lobbyId + "/chat", ChatMessageDTO.class, handler);
    }

    public StompSession.Subscription subscribeToFriendChat(UUID chatId, Consumer<ChatMessageDTO> handler) {
        return subscribe("/topic/friends/chats/" + chatId, ChatMessageDTO.class, handler);
    }

    public StompSession.Subscription subscribeToFriendChatNotifications(Consumer<ChatMessageDTO> handler) {
        return subscribe("/user/queue/friends/chat", ChatMessageDTO.class, handler);
    }
}
