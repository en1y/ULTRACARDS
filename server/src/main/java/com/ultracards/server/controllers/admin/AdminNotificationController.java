package com.ultracards.server.controllers.admin;

import com.ultracards.gateway.dto.admin.AdminNotificationRequestDTO;
import com.ultracards.gateway.dto.notifications.NotificationDTO;
import com.ultracards.server.entity.UserEntity;
import com.ultracards.server.service.admin.AdminNotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/v1/notifications")
@PreAuthorize("hasRole(T(com.ultracards.server.enums.UserRole).ADMIN.name())")
@RequiredArgsConstructor
public class AdminNotificationController {
    private final AdminNotificationService adminNotificationService;

    @PostMapping("/users/{userId}")
    public NotificationDTO sendToUser(@AuthenticationPrincipal UserEntity actor, @PathVariable Long userId,
                                      @RequestBody AdminNotificationRequestDTO request) {
        return adminNotificationService.sendToUser(actor, userId, request);
    }

    @PostMapping("/all")
    public List<NotificationDTO> sendToAll(@AuthenticationPrincipal UserEntity actor,
                                           @RequestBody AdminNotificationRequestDTO request) {
        return adminNotificationService.sendToAll(actor, request);
    }
}
