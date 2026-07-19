package com.ultracards.server.controllers.admin;

import com.ultracards.gateway.dto.admin.AdminAuditEventDTO;
import com.ultracards.gateway.dto.admin.AdminPageDTO;
import com.ultracards.server.service.admin.AdminAuditService;
import com.ultracards.server.service.admin.AdminUndoService;
import com.ultracards.server.entity.UserEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

import java.util.UUID;

@RestController
@RequestMapping("/api/admin/v1/audit")
@PreAuthorize("hasRole(T(com.ultracards.server.enums.UserRole).ADMIN.name())")
public class AdminAuditController {
    private final AdminAuditService adminAuditService;
    private final AdminUndoService adminUndoService;

    public AdminAuditController(AdminAuditService adminAuditService) {
        this(adminAuditService, null);
    }

    @Autowired
    public AdminAuditController(AdminAuditService adminAuditService, AdminUndoService adminUndoService) {
        this.adminAuditService = adminAuditService;
        this.adminUndoService = adminUndoService;
    }

    @GetMapping
    public AdminPageDTO<AdminAuditEventDTO> list(@RequestParam(defaultValue = "0") int page,
                                                  @RequestParam(defaultValue = "25") int size,
                                                  @RequestParam(required = false) String targetType,
                                                  @RequestParam(required = false) String targetId) {
        return adminAuditService.list(page, size, targetType, targetId);
    }

    @GetMapping("/{id}")
    public AdminAuditEventDTO get(@PathVariable UUID id) {
        return adminAuditService.get(id);
    }

    @PostMapping("/{id}/undo")
    public void undo(@AuthenticationPrincipal UserEntity actor, @PathVariable UUID id, @RequestParam String reason) {
        if (adminUndoService == null) throw new IllegalStateException("Undo service is not configured");
        adminUndoService.undo(actor, id, reason);
    }
}
