package com.ultracards.server.controllers.admin;

import com.ultracards.gateway.dto.admin.*;
import com.ultracards.server.service.admin.AdminReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/v1/reports")
@PreAuthorize("hasRole(T(com.ultracards.server.enums.UserRole).ADMIN.name())")
@RequiredArgsConstructor
public class AdminReportController {
    private final AdminReportService adminReportService;

    @GetMapping("/overview")
    public AdminOverviewDTO overview() { return adminReportService.overview(); }

    @GetMapping("/users")
    public AdminPageDTO<AdminUserSummaryDTO> users(@RequestParam(defaultValue = "0") int page,
                                                   @RequestParam(defaultValue = "25") int size,
                                                   @RequestParam(required = false) String query,
                                                   @RequestParam(defaultValue = "false") boolean exact,
                                                   @RequestParam(required = false) String status,
                                                   @RequestParam(required = false) String role,
                                                   @RequestParam(required = false) String sort,
                                                   @RequestParam(required = false) String direction) {
        return adminReportService.users(page, size, query, exact, status, role, sort, direction);
    }

    @GetMapping("/database")
    public AdminDatabaseOverviewDTO database() { return adminReportService.database(); }

    @GetMapping("/games")
    public AdminPageDTO<AdminRecordedGameDTO> games(@RequestParam(defaultValue = "0") int page,
                                                     @RequestParam(defaultValue = "25") int size,
                                                     @RequestParam(required = false) String gameType,
                                                     @RequestParam(required = false) Boolean completed,
                                                     @RequestParam(required = false) String mode,
                                                     @RequestParam(required = false) String sort,
                                                     @RequestParam(required = false) String direction) {
        return adminReportService.games(page, size, gameType, completed, mode, sort, direction);
    }

    @GetMapping("/sessions")
    public AdminPageDTO<AdminSessionDTO> sessions(@RequestParam(defaultValue = "0") int page,
                                                   @RequestParam(defaultValue = "25") int size,
                                                   @RequestParam(required = false) Long userId,
                                                   @RequestParam(required = false) Boolean valid,
                                                   @RequestParam(required = false) String sort,
                                                   @RequestParam(required = false) String direction) {
        return adminReportService.sessions(page, size, userId, valid, sort, direction);
    }
}
