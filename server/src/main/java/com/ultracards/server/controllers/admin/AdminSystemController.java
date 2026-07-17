package com.ultracards.server.controllers.admin;

import com.ultracards.gateway.dto.admin.AdminStatusDTO;
import com.ultracards.server.service.admin.AdminSystemService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/v1/system")
@PreAuthorize("hasRole(T(com.ultracards.server.enums.UserRole).ADMIN.name())")
@RequiredArgsConstructor
public class AdminSystemController {
    private final AdminSystemService adminSystemService;

    @GetMapping("/status")
    public AdminStatusDTO status() { return adminSystemService.status(); }
}
