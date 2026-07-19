package com.ultracards.server.controllers.admin;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class AdminTemplateRenderingTest {
    @Test
    void declaresVisibilityForEveryAdminRoute() throws IOException {
        try (var stream = getClass().getResourceAsStream("/templates/ui/admin.html")) {
            assertThat(stream).isNotNull();
            var template = new String(stream.readAllBytes());

            for (var page : new String[]{"dashboard", "users", "stats", "lobbies", "games", "sessions", "availability", "database", "audit", "notifications"}) {
                assertThat(template).contains("data-section=\"" + page + "\" th:hidden=\"${adminPage != '" + page + "'}\"");
            }
        }
    }

    @Test
    void usesTheAdminDialogInsteadOfBrowserPrompts() throws IOException {
        try (var stream = getClass().getResourceAsStream("/static/js/ui/admin.js")) {
            assertThat(stream).isNotNull();
            var script = new String(stream.readAllBytes());

            assertThat(script).contains("admin-action-dialog");
            assertThat(script).doesNotContain("prompt(");
            assertThat(script).doesNotContain("confirm(");
        }
    }
}
