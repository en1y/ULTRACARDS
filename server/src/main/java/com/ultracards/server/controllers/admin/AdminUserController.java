package com.ultracards.server.controllers.admin;

import com.ultracards.gateway.dto.admin.AdminPageDTO;
import com.ultracards.gateway.dto.admin.AdminUserPatchDTO;
import com.ultracards.gateway.dto.admin.AdminUserSummaryDTO;
import com.ultracards.server.entity.UserEntity;
import com.ultracards.server.service.admin.AdminUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/v1/users")
@PreAuthorize("hasRole(T(com.ultracards.server.enums.UserRole).ADMIN.name())")
@RequiredArgsConstructor
public class AdminUserController {
    private final AdminUserService adminUserService;

    @GetMapping
    public AdminPageDTO<AdminUserSummaryDTO> list(@RequestParam(defaultValue = "0") int page,
                                                   @RequestParam(defaultValue = "25") int size) {
        return adminUserService.list(page, size);
    }

    @GetMapping("/{id}")
    public AdminUserSummaryDTO get(@PathVariable Long id) {
        return adminUserService.get(id);
    }

    @PatchMapping("/{id}")
    public AdminUserSummaryDTO patch(@AuthenticationPrincipal UserEntity actor, @PathVariable Long id,
                                     @RequestBody AdminUserPatchDTO patch) {
        return adminUserService.patch(actor, id, patch);
    }

    @PutMapping("/{id}/roles/{role}")
    public AdminUserSummaryDTO grantRole(@AuthenticationPrincipal UserEntity actor, @PathVariable Long id,
                                         @PathVariable String role, @RequestParam String reason) {
        return adminUserService.grantRole(actor, id, role, reason);
    }

    @DeleteMapping("/{id}/roles/{role}")
    public AdminUserSummaryDTO revokeRole(@AuthenticationPrincipal UserEntity actor, @PathVariable Long id,
                                          @PathVariable String role, @RequestParam String reason) {
        return adminUserService.revokeRole(actor, id, role, reason);
    }

    @DeleteMapping("/{id}/sessions")
    public void revokeSessions(@AuthenticationPrincipal UserEntity actor, @PathVariable Long id,
                               @RequestParam String reason) {
        adminUserService.revokeSessions(actor, id, reason);
    }
}
