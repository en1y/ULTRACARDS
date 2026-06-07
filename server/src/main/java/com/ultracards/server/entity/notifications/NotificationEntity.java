package com.ultracards.server.entity.notifications;

import com.ultracards.gateway.dto.games.GamePlayerDTO;
import com.ultracards.gateway.dto.notifications.NotificationDTO;
import com.ultracards.server.entity.UserEntity;
import com.ultracards.server.enums.NotificationType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notifications")
@NoArgsConstructor
@Getter
@Setter
public class NotificationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "recipient_user_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_notifications_recipient_user")
    )
    private UserEntity recipient;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "sender_user_id",
            foreignKey = @ForeignKey(name = "fk_notifications_sender_user")
    )
    private UserEntity sender;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private NotificationType type;

    @Column(length = 512)
    private String message;

    @Column(name = "lobby_id")
    private UUID lobbyId;

    @Column(name = "friend_request_id")
    private UUID friendRequestId;

    @Column(name = "is_read", nullable = false)
    private boolean read;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "read_at")
    private Instant readAt;

    public NotificationEntity(
            UserEntity recipient,
            UserEntity sender,
            NotificationType type,
            String message,
            UUID lobbyId
    ) {
        this(recipient, sender, type, message, lobbyId, null);
    }

    public NotificationEntity(
            UserEntity recipient,
            UserEntity sender,
            NotificationType type,
            String message,
            UUID lobbyId,
            UUID friendRequestId
    ) {
        this.recipient = recipient;
        this.sender = sender;
        this.type = type;
        this.message = message;
        this.lobbyId = lobbyId;
        this.friendRequestId = friendRequestId;
    }

    public void markRead() {
        read = true;
        readAt = Instant.now();
    }

    public void markUnread() {
        read = false;
        readAt = null;
    }

    public NotificationDTO toDto() {
        return new NotificationDTO(
                id,
                type.toDto(),
                message,
                lobbyId,
                friendRequestId,
                toUserDto(sender),
                toUserDto(recipient),
                read,
                createdAt,
                readAt
        );
    }

    private GamePlayerDTO toUserDto(UserEntity user) {
        if (user == null)
            return null;
        return new GamePlayerDTO(user.getUsername(), user.getId());
    }
}
