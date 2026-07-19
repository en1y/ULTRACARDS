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

    @Test
    void enterSavesOpenUserAndLobbyEditors() throws IOException {
        try (var stream = getClass().getResourceAsStream("/static/js/ui/admin.js")) {
            assertThat(stream).isNotNull();
            var script = new String(stream.readAllBytes());

            assertThat(script).contains("#admin-user-form, #admin-lobby-form");
            assertThat(script).contains("[data-action=\"save-user\"], [data-action=\"save-lobby\"]");
            assertThat(script).contains("if (form.reportValidity()) confirmButton.click();");
        }
    }

    @Test
    void givesEveryAdminDialogRoundedCorners() throws IOException {
        try (var stream = getClass().getResourceAsStream("/static/css/ui/admin.css")) {
            assertThat(stream).isNotNull();
            var stylesheet = new String(stream.readAllBytes());

            assertThat(stylesheet).contains(".admin-dialog");
            assertThat(stylesheet).contains("border-radius: 24px");
        }
    }

    @Test
    void presentsAdminHacksAsAnOnOffControl() throws IOException {
        try (var templateStream = getClass().getResourceAsStream("/templates/ui/fragments/header/actions.html");
             var scriptStream = getClass().getResourceAsStream("/static/js/ui/fragments/header/profile-menu.js");
             var stylesheetStream = getClass().getResourceAsStream("/static/css/ui/fragments/header.css")) {
            assertThat(templateStream).isNotNull();
            assertThat(scriptStream).isNotNull();
            assertThat(stylesheetStream).isNotNull();
            var template = new String(templateStream.readAllBytes());
            var script = new String(scriptStream.readAllBytes());
            var stylesheet = new String(stylesheetStream.readAllBytes());

            assertThat(template).contains("header.adminHacks.enableTitle");
            assertThat(template).contains("data-admin-mode-lock-dialog");
            assertThat(template).doesNotContain("fake-admin-button-badge");
            assertThat(script).contains("header.adminHacks.disableTitle");
            assertThat(script).contains("/api/admin-mode/toggle");
            assertThat(script).contains("uc-admin-hacks-locked-until");
            assertThat(script).contains("60 * 60 * 1000");
            assertThat(script).contains("showAdminModeLock");
            assertThat(script).contains("aria-disabled");
            assertThat(stylesheet).contains(".fake-admin-button[aria-pressed=\"true\"]");
            assertThat(stylesheet).contains("var(--color-success)");
        }
    }
}
