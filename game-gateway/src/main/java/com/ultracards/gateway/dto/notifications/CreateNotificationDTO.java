package com.ultracards.gateway.dto.notifications;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CreateNotificationDTO {
    @NotNull private Long recipientUserId;
    @NotNull private NotificationTypeDTO type;
    private String message;
    private UUID lobbyId;
}
