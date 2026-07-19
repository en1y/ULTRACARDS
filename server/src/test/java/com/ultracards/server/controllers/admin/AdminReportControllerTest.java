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
        controller.users(2, 20, "en1y", true, "ACTIVE", "ADMIN", "username", "asc");

        verify(service).users(2, 20, "en1y", true, "ACTIVE", "ADMIN", "username", "asc");
    }

    @Test
    void exposesTheReadOnlyDatabaseOverview() {
        controller.database();

        verify(service).database();
    }
}
