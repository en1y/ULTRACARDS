package com.ultracards.server.service.notifications;

import com.ultracards.gateway.dto.notifications.CreateNotificationDTO;
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

    public List<NotificationDTO> getNotifications(UserEntity user) {
        return toDtos(notificationRepository.findByRecipientIdOrderByCreatedAtDesc(user.getId()));
    }

    public List<NotificationDTO> getUnreadNotifications(UserEntity user) {
        return toDtos(notificationRepository.findByRecipientIdAndReadFalseOrderByCreatedAtDesc(user.getId()));
    }

    public NotificationDTO createNotification(UserEntity sender, CreateNotificationDTO request) {
        var recipient = userRepository.findById(request.getRecipientUserId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Recipient user not found"));

        var type = NotificationType.from(request.getType());
        var message = sanitizeMessage(request.getMessage());
        validate(type, message, request.getLobbyId());

        var notification = new NotificationEntity(
                recipient,
                sender,
                type,
                message,
                request.getLobbyId()
        );

        return notificationRepository.save(notification).toDto();
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

    private NotificationEntity getOwnedNotification(UserEntity user, UUID notificationId) {
        return notificationRepository.findByIdAndRecipientId(notificationId, user.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification not found"));
    }

    private String sanitizeMessage(String message) {
        if (message == null)
            return null;

        var sanitized = NO_HTML_POLICY.sanitize(message).trim();
        if (sanitized.length() > MAX_MESSAGE_LENGTH)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "message cannot exceed 512 characters");

        return sanitized;
    }

    private void validate(NotificationType type, String message, UUID lobbyId) {
        if (type == NotificationType.GAME_INVITE && lobbyId == null)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "GAME_INVITE notifications require lobbyId");

        if (type == NotificationType.TEXT && !StringUtils.hasText(message))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "TEXT notifications require a message");
    }

    private List<NotificationDTO> toDtos(List<NotificationEntity> notifications) {
        var dtos = new ArrayList<NotificationDTO>();
        for (var notification : notifications) {
            dtos.add(notification.toDto());
        }
        return dtos;
    }
}
