package com.ultracards.server.service.admin;

import com.ultracards.gateway.dto.admin.AdminPointEventDTO;
import com.ultracards.gateway.dto.admin.AdminPointEventPatchDTO;
import com.ultracards.gateway.dto.admin.AdminPointsSettingsPatchDTO;
import com.ultracards.gateway.dto.points.PointsSettingsDTO;
import com.ultracards.server.entity.UserEntity;
import com.ultracards.server.service.points.PointsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdminEconomyService {
    private final PointsService points;
    private final AdminAuditService audit;

    public PointsSettingsDTO settings() {
        return points.settings();
    }

    @Transactional
    public PointsSettingsDTO updateSettings(UserEntity actor, AdminPointsSettingsPatchDTO patch) {
        requireReason(patch == null ? null : patch.reason());
        if (patch.wagerFeePercent() == null)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bet fee percentage is required");
        var previous = points.settings().wagerFeePercent();
        var updated = points.updateWagerFeePercent(patch.wagerFeePercent(), actor.getId());
        audit.record(actor.getId(), "UPDATE_WAGER_FEE", "POINTS", "SETTINGS", patch.reason().trim(),
                previous + "% -> " + updated.wagerFeePercent() + "%", "SUCCESS");
        return updated;
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

    private void requireReason(String reason) {
        if (reason == null || reason.isBlank())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A nonblank reason is required");
    }
}
