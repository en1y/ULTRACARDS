package com.ultracards.server.controllers.admin;

import com.ultracards.server.service.admin.*;
import com.ultracards.server.service.games.GameAvailabilityService;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;

import static org.assertj.core.api.Assertions.assertThat;

class AdminControllerArchitectureTest {
    @Test
    void everyAdminDomainUsesADedicatedControllerAndService() {
        assertController(AdminLobbyController.class, AdminLobbyService.class);
        assertController(AdminUserController.class, AdminUserService.class);
        assertController(AdminGameRecordController.class, AdminGameRecordService.class);
        assertController(AdminStatsController.class, AdminStatsService.class);
        assertController(AdminReportController.class, AdminReportService.class);
        assertController(AdminAuditController.class, AdminAuditService.class);
        assertController(AdminSystemController.class, AdminSystemService.class);
        assertController(AdminNotificationController.class, AdminNotificationService.class);
        assertController(AdminGameAvailabilityController.class, GameAvailabilityService.class);
    }

    private void assertController(Class<?> controller, Class<?> service) {
        var mapping = controller.getAnnotation(RequestMapping.class);
        assertThat(mapping).isNotNull();
        assertThat(mapping.value()).singleElement().asString().startsWith("/api/admin/v1/");
        assertThat(controller.getAnnotation(PreAuthorize.class)).isNotNull();
        assertThat(controller.getDeclaredFields()).anyMatch(field -> field.getType().equals(service));
        assertThat(controller.getDeclaredFields()).noneMatch(field -> field.getType().getSimpleName().endsWith("Repository"));
    }
}
