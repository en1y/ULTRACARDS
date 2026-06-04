package com.ultracards.server.service.notifications;

import com.ultracards.gateway.dto.notifications.NotificationDTO;
import com.ultracards.server.entity.UserEntity;
import com.ultracards.server.entity.notifications.NotificationEntity;
import com.ultracards.server.enums.NotificationType;
import com.ultracards.server.repositories.UserRepository;
import com.ultracards.server.repositories.notifications.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.owasp.html.HtmlPolicyBuilder;
import org.owasp.html.PolicyFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private static final PolicyFactory NO_HTML_POLICY = new HtmlPolicyBuilder().toFactory();
    private static final int MAX_MESSAGE_LENGTH = 512;

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final NotificationEventPublisher eventPublisher;

    public List<NotificationDTO> getNotifications(UserEntity user) {
        return toDtos(notificationRepository.findByRecipientIdOrderByCreatedAtDesc(user.getId()));
    }

    public List<NotificationDTO> getUnreadNotifications(UserEntity user) {
        return toDtos(notificationRepository.findByRecipientIdAndReadFalseOrderByCreatedAtDesc(user.getId()));
    }

    public NotificationDTO createTextNotification(Long recipientUserId, String message) {
        return createTextNotification(null, recipientUserId, message);
    }

    public NotificationDTO createTextNotification(UserEntity sender, Long recipientUserId, String message) {
        var recipient = userRepository.findById(recipientUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Recipient user not found"));
        var sanitizedMessage = sanitizeMessage(message);
        validate(NotificationType.TEXT, sanitizedMessage, null, null);

        var notification = new NotificationEntity(
                recipient,
                sender,
                NotificationType.TEXT,
                sanitizedMessage,
                null
        );

        return createNotification(notification);
    }

    public List<NotificationDTO> createTextNotificationToAll(UserEntity sender, String message) {
        var sanitizedMessage = sanitizeMessage(message);
        validate(NotificationType.TEXT, sanitizedMessage, null, null);

        var notifications = new ArrayList<NotificationEntity>();
        for (var recipient : userRepository.findAll()) {
            notifications.add(new NotificationEntity(
                    recipient,
                    sender,
                    NotificationType.TEXT,
                    sanitizedMessage,
                    null
            ));
        }

        var dtos = new ArrayList<NotificationDTO>();
        for (var notification : notifications)
            dtos.add(createNotification(notification));

        return dtos;
    }

    public NotificationDTO createFriendInviteNotification(UserEntity sender, UserEntity recipient, UUID friendRequestId) {
        if (friendRequestId == null)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "FRIEND_INVITE notifications require friendRequestId");

        var notification = new NotificationEntity(
                recipient,
                sender,
                NotificationType.FRIEND_INVITE,
                sender.getUsername() + " sent you a friend request.",
                null,
                friendRequestId
        );

        return createNotification(notification);
    }

    public NotificationDTO createGameInviteNotification(UserEntity sender, UserEntity recipient, UUID lobbyId) {
        if (lobbyId == null)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "GAME_INVITE notifications require lobbyId");

        var notification = new NotificationEntity(
                recipient,
                sender,
                NotificationType.GAME_INVITE,
                sender.getUsername() + " invited you to a lobby.",
                lobbyId
        );

        return createNotification(notification);
    }

    public boolean hasGameInvite(UserEntity recipient, UUID lobbyId) {
        return notificationRepository.existsByRecipientIdAndTypeAndLobbyId(
                recipient.getId(),
                NotificationType.GAME_INVITE,
                lobbyId
        );
    }

    @Transactional
    public void deleteGameInviteNotifications(UUID lobbyId) {
        if (lobbyId == null)
            return;

        notificationRepository.deleteByTypeAndLobbyId(NotificationType.GAME_INVITE, lobbyId);
    }

    public NotificationDTO markRead(UserEntity user, UUID notificationId) {
        var notification = getOwnedNotification(user, notificationId);
        notification.markRead();
        return notificationRepository.save(notification).toDto();
    }

    public NotificationDTO markUnread(UserEntity user, UUID notificationId) {
        var notification = getOwnedNotification(user, notificationId);
        notification.markUnread();
        return notificationRepository.save(notification).toDto();
    }

    public void deleteNotification(UserEntity user, UUID notificationId) {
        var notification = getOwnedNotification(user, notificationId);
        notificationRepository.delete(notification);
    }

    private NotificationDTO createNotification(NotificationEntity notification) {
        var savedNotification = notificationRepository.save(notification);
        eventPublisher.publish(savedNotification);
        return savedNotification.toDto();
    }

    private NotificationEntity getOwnedNotification(UserEntity user, UUID notificationId) {
        return notificationRepository.findByIdAndRecipientId(notificationId, user.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification not found"));
    }

    private String sanitizeMessage(String message) {
        if (message == null) return null;

        var sanitized = NO_HTML_POLICY.sanitize(message).trim();
        if (sanitized.length() > MAX_MESSAGE_LENGTH)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "message cannot exceed 512 characters");

        return sanitized;
    }

    private void validate(NotificationType type, String message, UUID lobbyId, UUID friendRequestId) {
        if (type == NotificationType.GAME_INVITE && lobbyId == null)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "GAME_INVITE notifications require lobbyId");

        if (type == NotificationType.FRIEND_INVITE && friendRequestId == null)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "FRIEND_INVITE notifications require friendRequestId");

        if (type == NotificationType.TEXT && !StringUtils.hasText(message))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "TEXT notifications require a message");
    }

    private List<NotificationDTO> toDtos(List<NotificationEntity> notifications) {
        var dtos = new ArrayList<NotificationDTO>();
        for (var notification : notifications)
            dtos.add(notification.toDto());

        return dtos;
    }

}
