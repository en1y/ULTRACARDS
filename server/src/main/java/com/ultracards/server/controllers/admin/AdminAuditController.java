package com.ultracards.server.controllers.admin;

import com.ultracards.gateway.dto.admin.AdminAuditEventDTO;
import com.ultracards.gateway.dto.admin.AdminPageDTO;
import com.ultracards.server.service.admin.AdminAuditService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.UUID;

@RestController
@RequestMapping("/api/admin/v1/audit")
@PreAuthorize("hasRole(T(com.ultracards.server.enums.UserRole).ADMIN.name())")
@RequiredArgsConstructor
public class AdminAuditController {
    private final AdminAuditService adminAuditService;

    @GetMapping
    public AdminPageDTO<AdminAuditEventDTO> list(@RequestParam(defaultValue = "0") int page,
                                                  @RequestParam(defaultValue = "25") int size) {
        return adminAuditService.list(page, size);
    }

    @GetMapping("/{id}")
    public AdminAuditEventDTO get(@PathVariable UUID id) {
        return adminAuditService.get(id);
    }
}
