package com.ultracards.server.controllers.admin;

import com.ultracards.gateway.dto.admin.AdminStatsDTO;
import com.ultracards.gateway.dto.admin.AdminStatsDiffDTO;
import com.ultracards.gateway.dto.admin.AdminStatsPatchDTO;
import com.ultracards.server.entity.UserEntity;
import com.ultracards.server.service.admin.AdminStatsService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/v1/stats/users/{userId}")
@PreAuthorize("hasRole(T(com.ultracards.server.enums.UserRole).ADMIN.name())")
@RequiredArgsConstructor
public class AdminStatsController {
    private final AdminStatsService adminStatsService;

    @GetMapping
    public AdminStatsDTO get(@PathVariable Long userId) { return adminStatsService.get(userId); }

    @PatchMapping("/{gameType}/{mode}")
    public AdminStatsDiffDTO patch(@AuthenticationPrincipal UserEntity actor, @PathVariable Long userId,
                                   @PathVariable String gameType, @PathVariable String mode,
                                   @RequestBody AdminStatsPatchDTO patch) {
        return adminStatsService.patch(actor, userId, gameType, mode, patch);
    }

    @PostMapping("/rebuild")
    public AdminStatsDiffDTO rebuild(@AuthenticationPrincipal UserEntity actor, @PathVariable Long userId,
                                     @RequestParam(required = false) String gameType,
                                     @RequestParam String reason,
                                     @RequestParam(defaultValue = "false") boolean dryRun) {
        return adminStatsService.rebuild(actor, userId, gameType, reason, dryRun);
    }
}
