package com.ultracards.gateway.dto.notifications;

import com.ultracards.gateway.dto.games.GamePlayerDTO;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@NoArgsConstructor
public class NotificationDTO {
    private UUID id;
    private NotificationTypeDTO type;
    private String message;
    private UUID lobbyId;
    private UUID friendRequestId;
    private GamePlayerDTO sender;
    private GamePlayerDTO recipient;
    private boolean read;
    private Instant createdAt;
    private Instant readAt;

    public NotificationDTO(
            UUID id,
            NotificationTypeDTO type,
            String message,
            UUID lobbyId,
            GamePlayerDTO sender,
            GamePlayerDTO recipient,
            boolean read,
            Instant createdAt,
            Instant readAt
    ) {
        this(id, type, message, lobbyId, null, sender, recipient, read, createdAt, readAt);
    }

    public NotificationDTO(
            UUID id,
            NotificationTypeDTO type,
            String message,
            UUID lobbyId,
            UUID friendRequestId,
            GamePlayerDTO sender,
            GamePlayerDTO recipient,
            boolean read,
            Instant createdAt,
            Instant readAt
    ) {
        this.id = id;
        this.type = type;
        this.message = message;
        this.lobbyId = lobbyId;
        this.friendRequestId = friendRequestId;
        this.sender = sender;
        this.recipient = recipient;
        this.read = read;
        this.createdAt = createdAt;
        this.readAt = readAt;
    }
}
