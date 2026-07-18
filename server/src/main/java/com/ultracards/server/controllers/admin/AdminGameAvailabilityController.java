package com.ultracards.server.controllers.admin;

import com.ultracards.gateway.dto.admin.AdminGameAvailabilityDTO;
import com.ultracards.gateway.dto.admin.AdminGameAvailabilityPatchDTO;
import com.ultracards.server.entity.UserEntity;
import com.ultracards.server.service.admin.AdminAuditService;
import com.ultracards.server.service.games.GameAvailabilityService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/admin/v1/games")
@PreAuthorize("hasRole(T(com.ultracards.server.enums.UserRole).ADMIN.name())")
@RequiredArgsConstructor
public class AdminGameAvailabilityController {
    private final GameAvailabilityService gameAvailabilityService;
    private final AdminAuditService auditService;

    @GetMapping
    public List<AdminGameAvailabilityDTO> list() { return gameAvailabilityService.list(); }

    @PatchMapping("/{game}")
    public AdminGameAvailabilityDTO patch(@AuthenticationPrincipal UserEntity actor, @PathVariable String game,
                                          @RequestBody AdminGameAvailabilityPatchDTO patch) {
        if (patch.reason() == null || patch.reason().isBlank())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A nonblank reason is required");
        var result = gameAvailabilityService.setEnabled(game, patch.mode(), patch.enabled());
        var target = result.mode() == null ? result.game() : result.game() + ":" + result.mode();
        auditService.record(actor.getId(), result.enabled() ? "ENABLE_GAME" : "DISABLE_GAME", "GAME", target,
                patch.reason(), result.enabled() ? "enabled" : "disabled", "SUCCESS");
        return result;
    }
}
