package com.ultracards.server.service.admin;

import com.ultracards.gateway.dto.admin.AdminDailyGoalDTO;
import com.ultracards.gateway.dto.admin.AdminDailyGoalsPatchDTO;
import com.ultracards.gateway.dto.admin.AdminPointEventDTO;
import com.ultracards.gateway.dto.admin.AdminPointEventPatchDTO;
import com.ultracards.gateway.dto.admin.AdminEconomyDashboardDTO;
import com.ultracards.gateway.dto.admin.AdminPointsSettingsPatchDTO;
import com.ultracards.gateway.dto.admin.AdminWagerFeeDTO;
import com.ultracards.gateway.dto.admin.AdminWagerFeePatchDTO;
import com.ultracards.gateway.dto.points.PointsSettingsDTO;
import com.ultracards.server.entity.UserEntity;
import com.ultracards.server.enums.games.GameType;
import com.ultracards.server.service.games.GameAvailabilityService;
import com.ultracards.server.service.points.PointsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdminEconomyService {
    private final PointsService points;
    private final GameAvailabilityService availability;
    private final AdminAuditService audit;

    public PointsSettingsDTO settings() {
        return points.settings();
    }

    public AdminEconomyDashboardDTO dashboard() {
        return points.adminDashboard();
    }

    @Transactional
    public PointsSettingsDTO updateSettings(UserEntity actor, AdminPointsSettingsPatchDTO patch) {
        requireReason(patch == null ? null : patch.reason());
        var previous = points.settings();
        // A PATCH that names one setting leaves the others where they are.
        var updated = points.updateSettings(patch.wagerFeePercent(), patch.startingBalance(), patch.dailyReward(),
                actor.getId());
        audit.record(actor.getId(), "UPDATE_POINT_SETTINGS", "POINTS", "SETTINGS", patch.reason().trim(),
                "fee %d%% -> %d%%, start %dP -> %dP, daily %dP -> %dP".formatted(
                        previous.wagerFeePercent(), updated.wagerFeePercent(),
                        previous.startingBalance(), updated.startingBalance(),
                        previous.dailyReward(), updated.dailyReward()), "SUCCESS");
        return updated;
    }

    public List<AdminDailyGoalDTO> dailyGoals() {
        return points.adminDailyGoals();
    }

    @Transactional
    public List<AdminDailyGoalDTO> updateDailyGoals(UserEntity actor, AdminDailyGoalsPatchDTO patch) {
        requireReason(patch == null ? null : patch.reason());
        var updated = points.replaceDailyGoals(patch.goals());
        audit.record(actor.getId(), "UPDATE_DAILY_GOALS", "POINTS", "DAILY_GOALS", patch.reason().trim(),
                updated.size() + " goals", "SUCCESS");
        return updated;
    }

    /**
     * Every game and mode with the fee it currently charges. Rows without their own
     * override report the fee they inherit, so the admin sees what players actually pay.
     */
    public List<AdminWagerFeeDTO> fees() {
        var overrides = points.wagerFees();
        var global = points.settings().wagerFeePercent();
        var output = new ArrayList<AdminWagerFeeDTO>();
        for (var game : GameType.values()) {
            var gameFee = overrides.get(game.name() + ':' + GameAvailabilityService.ALL_MODES);
            output.add(new AdminWagerFeeDTO(game.name(), null, gameFee == null ? global : gameFee, gameFee == null));
            for (var mode : availability.modes(game)) {
                var modeFee = overrides.get(game.name() + ':' + mode);
                output.add(new AdminWagerFeeDTO(game.name(), mode,
                        modeFee == null ? (gameFee == null ? global : gameFee) : modeFee, modeFee == null));
            }
        }
        return output;
    }

    @Transactional
    public AdminWagerFeeDTO updateFee(UserEntity actor, String gameValue, AdminWagerFeePatchDTO patch) {
        requireReason(patch == null ? null : patch.reason());
        if (patch.feePercent() == null)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bet fee percentage is required");
        var game = availability.game(gameValue);
        var mode = availability.mode(game, patch.mode());
        points.setWagerFee(game, mode, patch.feePercent());
        audit.record(actor.getId(), "UPDATE_WAGER_FEE", "POINTS", feeTarget(game, mode), patch.reason().trim(),
                patch.feePercent() + "%", "SUCCESS");
        return feeOf(game, mode);
    }

    @Transactional
    public AdminWagerFeeDTO resetFee(UserEntity actor, String gameValue, String modeValue, String reason) {
        requireReason(reason);
        var game = availability.game(gameValue);
        var mode = availability.mode(game, modeValue);
        points.resetWagerFee(game, mode);
        audit.record(actor.getId(), "RESET_WAGER_FEE", "POINTS", feeTarget(game, mode), reason.trim(),
                "inherited", "SUCCESS");
        return feeOf(game, mode);
    }

    private String feeTarget(GameType game, String mode) { return game.name() + ':' + mode; }

    private AdminWagerFeeDTO feeOf(GameType game, String mode) {
        var wanted = GameAvailabilityService.ALL_MODES.equals(mode) ? null : mode;
        for (var fee : fees()) if (fee.game().equals(game.name()) && java.util.Objects.equals(fee.mode(), wanted)) return fee;
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown bet fee rule");
    }

    public List<AdminPointEventDTO> events() {
        return points.adminEvents();
    }

    @Transactional
    public AdminPointEventDTO createEvent(UserEntity actor, AdminPointEventPatchDTO patch) {
        requireReason(patch == null ? null : patch.reason());
        var event = points.createEvent(patch);
        audit.record(actor.getId(), "CREATE_POINT_EVENT", "POINT_EVENT", event.id().toString(),
                patch.reason().trim(), event.name(), "SUCCESS");
        return event;
    }

    @Transactional
    public AdminPointEventDTO updateEvent(UserEntity actor, UUID id, AdminPointEventPatchDTO patch) {
        requireReason(patch == null ? null : patch.reason());
        var event = points.updateEvent(id, patch);
        audit.record(actor.getId(), "UPDATE_POINT_EVENT", "POINT_EVENT", id.toString(),
                patch.reason().trim(), event.name(), "SUCCESS");
        return event;
    }

    @Transactional
    public AdminPointEventDTO setEventEnabled(UserEntity actor, UUID id, boolean enabled, String reason) {
        requireReason(reason);
        var event = points.setEventEnabled(id, enabled);
        audit.record(actor.getId(), enabled ? "ENABLE_POINT_EVENT" : "DISABLE_POINT_EVENT", "POINT_EVENT",
                id.toString(), reason.trim(), event.name(), "SUCCESS");
        return event;
    }

    @Transactional
    public void deleteEvent(UserEntity actor, UUID id, String reason) {
        requireReason(reason);
        var event = points.deleteEvent(id);
        audit.record(actor.getId(), "DELETE_POINT_EVENT", "POINT_EVENT", id.toString(),
                reason.trim(), event.name(), "SUCCESS");
    }

    private void requireReason(String reason) {
        if (reason == null || reason.isBlank())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A nonblank reason is required");
    }
}
