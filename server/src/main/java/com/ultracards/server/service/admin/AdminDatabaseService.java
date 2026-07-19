package com.ultracards.server.service.admin;

import com.ultracards.gateway.dto.admin.AdminNotificationPatchDTO;
import com.ultracards.gateway.dto.admin.AdminPageDTO;
import com.ultracards.gateway.dto.notifications.NotificationDTO;
import com.ultracards.server.entity.UserEntity;
import com.ultracards.server.enums.NotificationType;
import com.ultracards.server.repositories.notifications.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;
import java.util.HashMap;

@Service
@RequiredArgsConstructor
public class AdminDatabaseService {
    private final NotificationRepository notificationRepository;
    private final AdminAuditService auditService;

    @Transactional(readOnly = true)
    public AdminPageDTO<NotificationDTO> notifications(int page, int size) {
        return notifications(page, size, null);
    }

    @Transactional(readOnly = true)
    public AdminPageDTO<NotificationDTO> notifications(int page, int size, Long recipientId) {
        return notifications(page, size, recipientId, null, null, null);
    }

    @Transactional(readOnly = true)
    public AdminPageDTO<NotificationDTO> notifications(int page, int size, Long recipientId, String typeValue,
                                                       Boolean read, String query) {
        NotificationType type = null;
        if (typeValue != null && !typeValue.isBlank()) {
            try {
                type = NotificationType.valueOf(typeValue.trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                throw badRequest("Unknown notification type: " + typeValue);
            }
        }
        var pattern = query == null || query.isBlank() ? null : "%" + query.trim().toLowerCase() + "%";
        var pageRequest = PageRequest.of(Math.max(0, page), Math.max(1, Math.min(100, size)), Sort.unsorted());
        var result = notificationRepository.findAdminReport(recipientId, type, read, pattern, pageRequest);
        return new AdminPageDTO<>(result.getContent().stream().map(notification -> notification.toDto()).toList(),
                result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    @Transactional
    public NotificationDTO patchNotification(UserEntity actor, UUID id, AdminNotificationPatchDTO patch) {
        requireReason(patch.reason());
        var notification = notificationRepository.findById(id).orElseThrow(() -> notFound("Notification not found"));
        var before = notificationState(notification);
        if (patch.message() != null) {
            var message = patch.message().trim();
            if (message.isBlank() || message.length() > 512) throw badRequest("Message must contain 1 to 512 characters");
            notification.setMessage(message);
        }
        if (patch.read() != null) {
            if (patch.read()) notification.markRead();
            else notification.markUnread();
        }
        notificationRepository.save(notification);
        auditService.record(actor.getId(), "UPDATE_NOTIFICATION", "NOTIFICATION", id.toString(), patch.reason(), "updated notification fields", "SUCCESS", before);
        return notification.toDto();
    }

    @Transactional
    public void deleteNotification(UserEntity actor, UUID id, String reason) {
        requireReason(reason);
        var notification = notificationRepository.findById(id).orElseThrow(() -> notFound("Notification not found"));
        var before = notificationState(notification);
        notificationRepository.delete(notification);
        auditService.record(actor.getId(), "DELETE_NOTIFICATION", "NOTIFICATION", id.toString(), reason, "deleted notification", "SUCCESS", before);
    }

    private HashMap<String, Object> notificationState(com.ultracards.server.entity.notifications.NotificationEntity n) {
        var state = new HashMap<String, Object>();
        state.put("id", n.getId()); state.put("recipientId", n.getRecipient().getId());
        state.put("senderId", n.getSender() == null ? null : n.getSender().getId());
        state.put("type", n.getType().name()); state.put("message", n.getMessage());
        state.put("lobbyId", n.getLobbyId()); state.put("friendRequestId", n.getFriendRequestId());
        state.put("read", n.isRead()); state.put("createdAt", n.getCreatedAt()); state.put("readAt", n.getReadAt());
        return state;
    }

    private void requireReason(String reason) {
        if (reason == null || reason.isBlank()) throw badRequest("A nonblank reason is required");
    }

    private ResponseStatusException badRequest(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
    private ResponseStatusException notFound(String message) { return new ResponseStatusException(HttpStatus.NOT_FOUND, message); }
}
