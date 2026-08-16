package com.ultracards.server.controllers.admin;

import com.ultracards.gateway.dto.admin.AdminPointEventDTO;
import com.ultracards.gateway.dto.admin.AdminPointEventPatchDTO;
import com.ultracards.gateway.dto.admin.AdminEconomyDashboardDTO;
import com.ultracards.gateway.dto.admin.AdminPointsSettingsPatchDTO;
import com.ultracards.gateway.dto.admin.AdminWagerFeeDTO;
import com.ultracards.gateway.dto.admin.AdminWagerFeePatchDTO;
import com.ultracards.gateway.dto.points.PointsSettingsDTO;
import com.ultracards.server.entity.UserEntity;
import com.ultracards.server.service.admin.AdminEconomyService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/v1/economy")
@PreAuthorize("hasRole(T(com.ultracards.server.enums.UserRole).ADMIN.name())")
@RequiredArgsConstructor
public class AdminEconomyController {
    private final AdminEconomyService economy;

    @GetMapping("/settings")
    public PointsSettingsDTO settings() {
        return economy.settings();
    }

    @GetMapping("/dashboard")
    public AdminEconomyDashboardDTO dashboard() {
        return economy.dashboard();
    }

    @PatchMapping("/settings")
    public PointsSettingsDTO updateSettings(@AuthenticationPrincipal UserEntity actor,
                                            @RequestBody AdminPointsSettingsPatchDTO patch) {
        return economy.updateSettings(actor, patch);
    }

    @GetMapping("/fees")
    public List<AdminWagerFeeDTO> fees() {
        return economy.fees();
    }

    @PatchMapping("/fees/{game}")
    public AdminWagerFeeDTO updateFee(@AuthenticationPrincipal UserEntity actor, @PathVariable String game,
                                      @RequestBody AdminWagerFeePatchDTO patch) {
        return economy.updateFee(actor, game, patch);
    }

    @DeleteMapping("/fees/{game}")
    public AdminWagerFeeDTO resetFee(@AuthenticationPrincipal UserEntity actor, @PathVariable String game,
                                     @RequestParam(required = false) String mode, @RequestParam String reason) {
        return economy.resetFee(actor, game, mode, reason);
    }

    @GetMapping("/events")
    public List<AdminPointEventDTO> events() {
        return economy.events();
    }

    @PostMapping("/events")
    public AdminPointEventDTO createEvent(@AuthenticationPrincipal UserEntity actor,
                                          @RequestBody AdminPointEventPatchDTO patch) {
        return economy.createEvent(actor, patch);
    }

    @PutMapping("/events/{id}")
    public AdminPointEventDTO updateEvent(@AuthenticationPrincipal UserEntity actor, @PathVariable UUID id,
                                          @RequestBody AdminPointEventPatchDTO patch) {
        return economy.updateEvent(actor, id, patch);
    }

    @PatchMapping("/events/{id}/enabled")
    public AdminPointEventDTO setEventEnabled(@AuthenticationPrincipal UserEntity actor, @PathVariable UUID id,
                                              @RequestParam boolean enabled, @RequestParam String reason) {
        return economy.setEventEnabled(actor, id, enabled, reason);
    }

    @DeleteMapping("/events/{id}")
    public void deleteEvent(@AuthenticationPrincipal UserEntity actor, @PathVariable UUID id,
                            @RequestParam String reason) {
        economy.deleteEvent(actor, id, reason);
    }
}
