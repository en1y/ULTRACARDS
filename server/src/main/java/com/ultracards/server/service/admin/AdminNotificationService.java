package com.ultracards.server.service.admin;

import com.ultracards.gateway.dto.admin.AdminNotificationRequestDTO;
import com.ultracards.gateway.dto.notifications.NotificationDTO;
import com.ultracards.server.entity.UserEntity;
import com.ultracards.server.service.notifications.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminNotificationService {
    private final NotificationService notificationService;
    private final AdminAuditService auditService;

    public NotificationDTO sendToUser(UserEntity actor, Long userId, AdminNotificationRequestDTO request) {
        validate(request);
        var result = notificationService.createTextNotification(actor, userId, request.message());
        auditService.record(actor.getId(), "SEND_NOTIFICATION", "USER", userId.toString(), request.reason(),
                "sent text notification of " + request.message().length() + " characters", "SUCCESS");
        return result;
    }

    public List<NotificationDTO> sendToAll(UserEntity actor, AdminNotificationRequestDTO request) {
        validate(request);
        var result = notificationService.createTextNotificationToAll(actor, request.message());
        auditService.record(actor.getId(), "BROADCAST_NOTIFICATION", "ALL_USERS", "all", request.reason(),
                "sent text notification to " + result.size() + " users", "SUCCESS");
        return result;
    }

    private void validate(AdminNotificationRequestDTO request) {
        if (request == null || request.message() == null || request.message().isBlank())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A message is required");
        if (request.reason() == null || request.reason().isBlank())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A nonblank reason is required");
    }
}
