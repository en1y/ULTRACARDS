package com.ultracards.server.entity.chat;

import com.ultracards.gateway.dto.games.chat.ChatDTO;
import com.ultracards.gateway.dto.games.chat.ChatMessageDTO;
import com.ultracards.server.entity.UserEntity;
import com.ultracards.server.service.chat.ChatMessage;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
public class ChatEntity {
    private final UUID id = UUID.randomUUID();
    private final List<ChatMessage> messages = new ArrayList<>();
    private final UUID lobbyId;
    private boolean isOpen = true;

    public ChatEntity (UUID lobbyId) {
        this.lobbyId = lobbyId;
    }

    public ChatMessage sendMessage(UserEntity user, String message) {
        var mssg = new ChatMessage(user, message);
        messages.add(mssg);
        return mssg;
    }

    public ChatDTO toDto() {
        var res = new ArrayList<ChatMessageDTO>();
        for (var m: messages)
            res.add(m.toDto());
        return new ChatDTO(res, isOpen);
    }

    public void open() {isOpen = true;}
    public void close() {isOpen = false;}
}
