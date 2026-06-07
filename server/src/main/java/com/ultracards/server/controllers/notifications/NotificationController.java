package com.ultracards.server.controllers.notifications;

import com.ultracards.gateway.dto.notifications.NotificationDTO;
import com.ultracards.server.entity.UserEntity;
import com.ultracards.server.service.notifications.NotificationService;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    @PreAuthorize("hasRole(T(com.ultracards.server.enums.UserRole).USER.name())")
    public ResponseEntity<List<NotificationDTO>> getNotifications(
            @AuthenticationPrincipal UserEntity user
    ) {
        return ResponseEntity.ok(notificationService.getNotifications(user));
    }

    @GetMapping("/unread")
    @PreAuthorize("hasRole(T(com.ultracards.server.enums.UserRole).USER.name())")
    public ResponseEntity<List<NotificationDTO>> getUnreadNotifications(
            @AuthenticationPrincipal UserEntity user
    ) {
        return ResponseEntity.ok(notificationService.getUnreadNotifications(user));
    }

    @PostMapping("/text/users/{recipientUserId}")
    @PreAuthorize("hasAnyRole(T(com.ultracards.server.enums.UserRole).MODERATOR.name(), T(com.ultracards.server.enums.UserRole).ADMIN.name())")
    public ResponseEntity<NotificationDTO> sendTextNotificationToUser(
            @AuthenticationPrincipal UserEntity sender,
            @PathVariable Long recipientUserId,
            @RequestBody @NotBlank String message
    ) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(notificationService.createTextNotification(sender, recipientUserId, message));
    }

    @PostMapping("/text/all")
    @PreAuthorize("hasAnyRole(T(com.ultracards.server.enums.UserRole).MODERATOR.name(), T(com.ultracards.server.enums.UserRole).ADMIN.name())")
    public ResponseEntity<List<NotificationDTO>> sendTextNotificationToAll(
            @AuthenticationPrincipal UserEntity sender,
            @RequestBody @NotBlank String message
    ) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(notificationService.createTextNotificationToAll(sender, message));
    }

    @PatchMapping("/{id}/read")
    @PreAuthorize("hasRole(T(com.ultracards.server.enums.UserRole).USER.name())")
    public ResponseEntity<NotificationDTO> markRead(
            @AuthenticationPrincipal UserEntity user,
            @PathVariable UUID id
    ) {
        return ResponseEntity.ok(notificationService.markRead(user, id));
    }

    @PatchMapping("/{id}/unread")
    @PreAuthorize("hasRole(T(com.ultracards.server.enums.UserRole).USER.name())")
    public ResponseEntity<NotificationDTO> markUnread(
            @AuthenticationPrincipal UserEntity user,
            @PathVariable UUID id
    ) {
        return ResponseEntity.ok(notificationService.markUnread(user, id));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole(T(com.ultracards.server.enums.UserRole).USER.name())")
    public ResponseEntity<Void> deleteNotification(
            @AuthenticationPrincipal UserEntity user,
            @PathVariable UUID id
    ) {
        notificationService.deleteNotification(user, id);
        return ResponseEntity.noContent().build();
    }
}
