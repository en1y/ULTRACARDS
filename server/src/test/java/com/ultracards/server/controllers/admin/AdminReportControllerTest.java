package com.ultracards.server.controllers.admin;

import com.ultracards.server.service.admin.AdminReportService;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class AdminReportControllerTest {
    private final AdminReportService service = mock(AdminReportService.class);
    private final AdminReportController controller = new AdminReportController(service);

    @Test
    void forwardsPreciseUserSearchToTheReportService() {
        controller.users(2, 20, "en1y", true, "ACTIVE", "ADMIN", "username", "asc", null, null, null);

        verify(service).users(2, 20, "en1y", true, "ACTIVE", "ADMIN", "username", "asc", null, null, null);
    }

    /** The advanced search sends per-field criteria alongside the loose query. */
    @Test
    void forwardsEveryAdvancedCriterionToTheReportService() {
        controller.users(0, 25, null, false, "ACTIVE", null, null, null, "en1y", "example.com", "7");

        verify(service).users(0, 25, null, false, "ACTIVE", null, null, null, "en1y", "example.com", "7");
    }

    @Test
    void exposesTheReadOnlyDatabaseOverview() {
        controller.database();

        verify(service).database();
    }
}
