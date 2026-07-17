package com.ultracards.server.controllers.admin;

import com.ultracards.gateway.dto.admin.AdminRecordedGameDTO;
import com.ultracards.gateway.dto.admin.AdminRecordedGamePatchDTO;
import com.ultracards.server.entity.UserEntity;
import com.ultracards.server.service.admin.AdminGameRecordService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/admin/v1/game-records")
@PreAuthorize("hasRole(T(com.ultracards.server.enums.UserRole).ADMIN.name())")
@RequiredArgsConstructor
public class AdminGameRecordController {
    private final AdminGameRecordService adminGameRecordService;

    @GetMapping("/{id}")
    public AdminRecordedGameDTO get(@PathVariable UUID id) { return adminGameRecordService.get(id); }

    @PatchMapping("/{id}")
    public AdminRecordedGameDTO patch(@AuthenticationPrincipal UserEntity actor, @PathVariable UUID id,
                                      @RequestBody AdminRecordedGamePatchDTO patch) {
        return adminGameRecordService.patch(actor, id, patch);
    }
}
