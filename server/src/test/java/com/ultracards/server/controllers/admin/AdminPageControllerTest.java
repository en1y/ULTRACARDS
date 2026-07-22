package com.ultracards.server.controllers.admin;

import com.ultracards.server.entity.UserEntity;
import org.junit.jupiter.api.Test;
import org.springframework.ui.ExtendedModelMap;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AdminPageControllerTest {
    private final AdminPageController controller = new AdminPageController();
    private final UserEntity admin = new UserEntity("admin@ultracards.test", "admin");

    @Test
    void rendersTheRequestedAdminSubpage() {
        var model = new ExtendedModelMap();

        var view = controller.admin("database", admin, model);

        assertEquals("ui/admin", view);
        assertEquals("database", model.getAttribute("adminPage"));
        assertEquals("admin", model.getAttribute("username"));
    }

    @Test
    void redirectsUnknownAdminSubpagesToTheDashboard() {
        var view = controller.admin("unknown", admin, new ExtendedModelMap());

        assertEquals("redirect:/admin", view);
    }

    @Test
    void rendersFrontendOnlySandbox() {
        var model = new ExtendedModelMap();

        var view = controller.sandbox(admin, model);

        assertEquals("ui/admin-sandbox", view);
        assertEquals("admin", model.getAttribute("username"));
    }
}
