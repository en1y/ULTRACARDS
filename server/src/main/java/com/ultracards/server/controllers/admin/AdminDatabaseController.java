package com.ultracards.server.controllers.admin;

import com.ultracards.gateway.dto.admin.AdminNotificationPatchDTO;
import com.ultracards.gateway.dto.admin.AdminPageDTO;
import com.ultracards.gateway.dto.notifications.NotificationDTO;
import com.ultracards.server.entity.UserEntity;
import com.ultracards.server.service.admin.AdminDatabaseService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/admin/v1/database")
@PreAuthorize("hasRole(T(com.ultracards.server.enums.UserRole).ADMIN.name())")
@RequiredArgsConstructor
public class AdminDatabaseController {
    private final AdminDatabaseService adminDatabaseService;

    @GetMapping("/notifications")
    public AdminPageDTO<NotificationDTO> notifications(@RequestParam(defaultValue = "0") int page,
                                                        @RequestParam(defaultValue = "25") int size,
                                                        @RequestParam(required = false) Long userId) {
        return adminDatabaseService.notifications(page, size, userId);
    }

    @PatchMapping("/notifications/{id}")
    public NotificationDTO patchNotification(@AuthenticationPrincipal UserEntity actor, @PathVariable UUID id,
                                             @RequestBody AdminNotificationPatchDTO patch) {
        return adminDatabaseService.patchNotification(actor, id, patch);
    }

    @DeleteMapping("/notifications/{id}")
    public void deleteNotification(@AuthenticationPrincipal UserEntity actor, @PathVariable UUID id,
                                   @RequestParam String reason) {
        adminDatabaseService.deleteNotification(actor, id, reason);
    }
}
