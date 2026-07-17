package com.ultracards.server.controllers.admin;

import com.ultracards.gateway.dto.admin.AdminLobbyDTO;
import com.ultracards.gateway.dto.admin.AdminLobbyExtendDTO;
import com.ultracards.gateway.dto.admin.AdminLobbyPatchDTO;
import com.ultracards.server.entity.UserEntity;
import com.ultracards.server.service.admin.AdminLobbyService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/v1/lobbies")
@PreAuthorize("hasRole(T(com.ultracards.server.enums.UserRole).ADMIN.name())")
@RequiredArgsConstructor
public class AdminLobbyController {
    private final AdminLobbyService adminLobbyService;

    @GetMapping
    public List<AdminLobbyDTO> list() { return adminLobbyService.list(); }

    @GetMapping("/{id}")
    public AdminLobbyDTO get(@PathVariable UUID id) { return adminLobbyService.get(id); }

    @PatchMapping("/{id}")
    public AdminLobbyDTO patch(@AuthenticationPrincipal UserEntity actor, @PathVariable UUID id,
                               @RequestBody AdminLobbyPatchDTO patch) {
        return adminLobbyService.patch(actor, id, patch);
    }

    @DeleteMapping("/{id}")
    public void close(@AuthenticationPrincipal UserEntity actor, @PathVariable UUID id,
                      @RequestParam String reason) {
        adminLobbyService.close(actor, id, reason);
    }

    @DeleteMapping("/{id}/players/{userId}")
    public AdminLobbyDTO kick(@AuthenticationPrincipal UserEntity actor, @PathVariable UUID id,
                              @PathVariable Long userId, @RequestParam String reason) {
        return adminLobbyService.kick(actor, id, userId, reason);
    }

    @PostMapping("/{id}/extend")
    public AdminLobbyDTO extend(@AuthenticationPrincipal UserEntity actor, @PathVariable UUID id,
                                @RequestBody AdminLobbyExtendDTO request) {
        return adminLobbyService.extend(actor, id, request.seconds(), request.reason());
    }
}
