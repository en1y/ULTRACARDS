package com.ultracards.server.controllers.admin;

import com.ultracards.server.entity.UserEntity;
import com.ultracards.server.service.admin.AdminSessionService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/admin/v1/sessions")
@PreAuthorize("hasRole(T(com.ultracards.server.enums.UserRole).ADMIN.name())")
@RequiredArgsConstructor
public class AdminSessionController {
    private final AdminSessionService adminSessionService;

    @PostMapping("/{id}/expire")
    public void expire(@AuthenticationPrincipal UserEntity actor, @PathVariable UUID id, @RequestParam String reason) {
        adminSessionService.expire(actor, id, reason);
    }

    @DeleteMapping("/{id}")
    public void delete(@AuthenticationPrincipal UserEntity actor, @PathVariable UUID id, @RequestParam String reason) {
        adminSessionService.delete(actor, id, reason);
    }
}
