package com.ultracards.gateway.dto.notifications;

import com.ultracards.gateway.dto.games.GamePlayerDTO;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class NotificationDTO {
    private UUID id;
    private NotificationTypeDTO type;
    private String message;
    private UUID lobbyId;
    private GamePlayerDTO sender;
    private GamePlayerDTO recipient;
    private boolean read;
    private Instant createdAt;
    private Instant readAt;
}
