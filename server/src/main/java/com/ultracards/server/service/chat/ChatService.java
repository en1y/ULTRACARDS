package com.ultracards.server.service.chat;

import com.ultracards.server.entity.UserEntity;
import com.ultracards.server.entity.chat.ChatEntity;
import com.ultracards.server.service.ultrakill.UltrakillLevelService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.StringJoiner;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ChatService{
    private final ChatManager chatManager;
    private final ChatEventPublisher eventPublisher;
    private final UltrakillLevelService ultrakillLevelService;
    private final UserEntity serverUser = new UserEntity("", "Server");

    public ChatEntity getChat(UUID lobbyId) {
        return chatManager.getChat(lobbyId);
    }

    public void sendMessage(UUID lobbyId, UserEntity sender, String message) {
        var chat = getChat(lobbyId);
        var messageObj = chat.sendMessage(sender, message);
        eventPublisher.publish(messageObj.toDto(), lobbyId);
        var levelNumbersInMessage = ultrakillLevelService.findLevelNumbers(messageObj.getMessage());
        if (levelNumbersInMessage.length > 0) {
            var serverMssg = new StringJoiner("\n");
            for (var s: ultrakillLevelService.createMessages(levelNumbersInMessage)) {
                serverMssg.add(s);
            }
            sendServerMessage(chat, serverMssg.toString());
        }
    }

    public void sendServerMessage(UUID lobbyId, String message) {
        sendServerMessage(getChat(lobbyId), message);
    }

    private void sendServerMessage(ChatEntity chat, String message) {
        var messageObj = chat.sendMessage(serverUser, message);
        eventPublisher.publish(messageObj.toDto(), chat.getLobbyId());
    }

    public void deleteChat(UUID lobbyId) {
        chatManager.deleteChat(lobbyId);
    }

    public void createChat(UUID lobbyId) {
        chatManager.createChat(lobbyId);
    }
}
