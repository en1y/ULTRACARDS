package com.ultracards.server.controllers.admin;

import com.ultracards.gateway.dto.admin.AdminPageDTO;
import com.ultracards.gateway.dto.admin.AdminUserPatchDTO;
import com.ultracards.gateway.dto.admin.AdminUserSummaryDTO;
import com.ultracards.gateway.dto.admin.AdminPointsAdjustmentDTO;
import com.ultracards.gateway.dto.admin.AdminPointsPatchDTO;
import com.ultracards.gateway.dto.points.PointTransactionPageDTO;
import com.ultracards.gateway.dto.points.PointsSeriesPointDTO;
import com.ultracards.server.entity.UserEntity;
import com.ultracards.server.service.admin.AdminUserService;
import com.ultracards.server.service.admin.AdminPointsService;
import com.ultracards.server.service.points.PointsService;
import lombok.RequiredArgsConstructor;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/v1/users")
@PreAuthorize("hasRole(T(com.ultracards.server.enums.UserRole).ADMIN.name())")
@RequiredArgsConstructor
public class AdminUserController {
    private final AdminUserService adminUserService;
    private final AdminPointsService adminPointsService;
    private final PointsService pointsService;

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

    @PatchMapping("/{id}/points")
    public AdminPointsAdjustmentDTO adjustPoints(@AuthenticationPrincipal UserEntity actor,
                                                  @PathVariable Long id,
                                                  @Valid @RequestBody AdminPointsPatchDTO patch) {
        return adminPointsService.adjust(actor, id, patch);
    }

    /** Where a user's Points went: the same ledger they see, for any account. */
    @GetMapping("/{id}/points")
    public PointTransactionPageDTO pointsLedger(@PathVariable Long id,
                                                @RequestParam(defaultValue = "0") int page,
                                                @RequestParam(defaultValue = "25") int size) {
        return pointsService.transactions(id, page, size);
    }

    @GetMapping("/{id}/points/series")
    public List<PointsSeriesPointDTO> pointsSeries(@PathVariable Long id,
                                                   @RequestParam(defaultValue = "30") int days) {
        return pointsService.series(id, Math.max(0, days));
    }
}
