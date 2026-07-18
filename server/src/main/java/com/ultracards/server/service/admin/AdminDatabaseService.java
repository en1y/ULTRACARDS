package com.ultracards.server.service.admin;

import com.ultracards.gateway.dto.admin.AdminNotificationPatchDTO;
import com.ultracards.gateway.dto.admin.AdminPageDTO;
import com.ultracards.gateway.dto.notifications.NotificationDTO;
import com.ultracards.server.entity.UserEntity;
import com.ultracards.server.repositories.notifications.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

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
        var pageRequest = PageRequest.of(Math.max(0, page), Math.max(1, Math.min(100, size)), Sort.unsorted());
        var result = recipientId == null
                ? notificationRepository.findAllByOrderByCreatedAtDesc(pageRequest)
                : notificationRepository.findByRecipientIdOrderByCreatedAtDesc(recipientId, pageRequest);
        return new AdminPageDTO<>(result.getContent().stream().map(notification -> notification.toDto()).toList(),
                result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    @Transactional
    public NotificationDTO patchNotification(UserEntity actor, UUID id, AdminNotificationPatchDTO patch) {
        requireReason(patch.reason());
        var notification = notificationRepository.findById(id).orElseThrow(() -> notFound("Notification not found"));
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
        auditService.record(actor.getId(), "UPDATE_NOTIFICATION", "NOTIFICATION", id.toString(), patch.reason(), "updated notification fields", "SUCCESS");
        return notification.toDto();
    }

    @Transactional
    public void deleteNotification(UserEntity actor, UUID id, String reason) {
        requireReason(reason);
        if (!notificationRepository.existsById(id)) throw notFound("Notification not found");
        notificationRepository.deleteById(id);
        auditService.record(actor.getId(), "DELETE_NOTIFICATION", "NOTIFICATION", id.toString(), reason, "deleted notification", "SUCCESS");
    }

    private void requireReason(String reason) {
        if (reason == null || reason.isBlank()) throw badRequest("A nonblank reason is required");
    }

    private ResponseStatusException badRequest(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
    private ResponseStatusException notFound(String message) { return new ResponseStatusException(HttpStatus.NOT_FOUND, message); }
}
