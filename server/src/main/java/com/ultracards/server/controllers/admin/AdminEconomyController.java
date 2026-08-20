package com.ultracards.server.controllers.admin;

import com.ultracards.gateway.dto.admin.AdminAchievementDTO;
import com.ultracards.gateway.dto.admin.AdminAchievementHolderDTO;
import com.ultracards.gateway.dto.admin.AdminAchievementsPatchDTO;
import com.ultracards.gateway.dto.admin.AdminPageDTO;
import com.ultracards.gateway.dto.admin.AdminStreakPatchDTO;
import com.ultracards.gateway.dto.admin.AdminDailyGoalDTO;
import com.ultracards.gateway.dto.admin.AdminDailyGoalsPatchDTO;
import com.ultracards.gateway.dto.admin.AdminPointEventDTO;
import com.ultracards.gateway.dto.admin.AdminPointEventPatchDTO;
import com.ultracards.gateway.dto.admin.AdminEconomyDashboardDTO;
import com.ultracards.gateway.dto.admin.AdminPointsSettingsPatchDTO;
import com.ultracards.gateway.dto.admin.AdminWagerFeeDTO;
import com.ultracards.gateway.dto.admin.AdminWagerFeePatchDTO;
import com.ultracards.gateway.dto.points.PointsSettingsDTO;
import com.ultracards.gateway.dto.points.PointsStreakDTO;
import com.ultracards.server.entity.UserEntity;
import com.ultracards.server.service.admin.AdminEconomyService;
import com.ultracards.server.service.points.MarkdownRenderer;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/v1/economy")
@PreAuthorize("hasRole(T(com.ultracards.server.enums.UserRole).ADMIN.name())")
@RequiredArgsConstructor
public class AdminEconomyController {
    private final AdminEconomyService economy;
    private final MarkdownRenderer markdown;

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

    @GetMapping("/daily-goals")
    public List<AdminDailyGoalDTO> dailyGoals() {
        return economy.dailyGoals();
    }

    @PutMapping("/daily-goals")
    public List<AdminDailyGoalDTO> updateDailyGoals(@AuthenticationPrincipal UserEntity actor,
                                                    @RequestBody AdminDailyGoalsPatchDTO patch) {
        return economy.updateDailyGoals(actor, patch);
    }

    @GetMapping("/achievements")
    public List<AdminAchievementDTO> achievements() {
        return economy.achievements();
    }

    @PutMapping("/achievements")
    public List<AdminAchievementDTO> updateAchievements(@AuthenticationPrincipal UserEntity actor,
                                                        @RequestBody AdminAchievementsPatchDTO patch) {
        return economy.updateAchievements(actor, patch);
    }

    @GetMapping("/achievements/{code}/users")
    public AdminPageDTO<AdminAchievementHolderDTO> achievementHolders(@PathVariable String code,
                                                                      @RequestParam(defaultValue = "0") int page,
                                                                      @RequestParam(defaultValue = "25") int size) {
        return economy.achievementHolders(code, page, size);
    }

    @GetMapping("/streaks/{userId}")
    public PointsStreakDTO streak(@PathVariable Long userId) {
        return economy.streak(userId);
    }

    @PatchMapping("/streaks/{userId}")
    public PointsStreakDTO updateStreak(@AuthenticationPrincipal UserEntity actor, @PathVariable Long userId,
                                        @RequestBody AdminStreakPatchDTO patch) {
        return economy.updateStreak(actor, userId, patch);
    }

    @DeleteMapping("/streaks/{userId}")
    public PointsStreakDTO resetStreak(@AuthenticationPrincipal UserEntity actor, @PathVariable Long userId,
                                       @RequestParam String reason) {
        return economy.resetStreak(actor, userId, reason);
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

    @PostMapping("/events/preview")
    public Map<String, String> previewEventDescription(@RequestBody MarkdownPreviewRequest request) {
        return Map.of("html", markdown.render(request == null ? null : request.description()));
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

    public record MarkdownPreviewRequest(String description) {
    }
}
