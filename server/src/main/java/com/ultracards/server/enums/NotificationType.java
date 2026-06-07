package com.ultracards.server.enums;

import com.ultracards.gateway.dto.notifications.NotificationTypeDTO;

public enum NotificationType {
    GAME_INVITE,
    FRIEND_INVITE,
    TEXT;

    public NotificationTypeDTO toDto() {
        return NotificationTypeDTO.valueOf(name());
    }

    public static NotificationType from(NotificationTypeDTO type) {
        return NotificationType.valueOf(type.name());
    }
}
