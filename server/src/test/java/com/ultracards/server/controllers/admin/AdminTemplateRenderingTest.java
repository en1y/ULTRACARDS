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

            for (var page : new String[]{"dashboard", "points", "events", "users", "stats", "lobbies", "games", "sessions", "availability", "database", "audit", "notifications"}) {
                assertThat(template).contains("data-section=\"" + page + "\" th:hidden=\"${adminPage != '" + page + "'}\"");
            }
        }
    }

    @Test
    void exposesSeparatePointSettingsAndWorkingEventActions() throws IOException {
        var template = resource("/templates/ui/admin.html");
        var script = resource("/static/js/ui/admin.js");

        assertThat(template).contains("id=\"admin-points-settings\"", "id=\"admin-fee-status\"", "id=\"admin-event-form\"",
                "admin-events-manager", "id=\"admin-event-submit\"", "name='startsAt'", "name='endsAt'", "name='gameTypes'",
                "id=\"admin-event-achievements\"", "id=\"admin-event-achievements-empty\"",
                "admin-event-section", "admin-event-submit-bar", "ui/fragments/date-time :: dateTime",
                "id=\"admin-points-metrics\"", "id=\"admin-points-total-chart\"", "id=\"admin-points-leaderboard\"",
                "data-results=\"economy-events\" aria-live=\"polite\"><p class=\"admin-empty\"",
                "rev='points-events-datetime-picker'", "/css/ui/fragments/date-time.css", "/js/ui/fragments/date-time.js")
                .doesNotContain("type=\"datetime-local\"", "id=\"admin-fee-example\"");
        assertThat(script).contains("eventAchievementEditor", "gamesRequired", "winsRequired",
                "lossesRequired", "drawsRequired", "/economy/events", "/economy/settings", "/economy/dashboard",
                "syncEventAchievementEditors", "admin-event-summary-card", "totalReward",
                "renderAchievementGameTypes", "selectedEventGameTypes", "gameTypes:", "hidden:",
                "oneAchievementRequired", ".admin-event-games input[name=\"gameTypes\"]",
                "button.disabled = true", "saved.wagerFeePercent", "feeStatus.textContent",
                "admin.economy.createEvent", "admin.economy.updateEvent", "delete-point-event",
                "method: \"DELETE\"", "const parseDateTime", "date.getHours() !== hour",
                "parseDateTime(form.elements.startsAt.value)", "parseDateTime(values.lastPlayedAt)",
                "const loadPoints", "const loadEvents", "syncDateTimeWidget", "renderPointsChart",
                // Goal fields must stay off the event's own field names, or form.elements.name becomes a
                // RadioNodeList and every create/update posts a blank name.
                "[name=\"goalName\"]", "[name=\"goalDescription\"]")
                .doesNotContain("node.querySelector('[name=\"name\"]')", "node.querySelector('[name=\"description\"]')",
                        "renderFeeExample", "const totalReward = Number(event.completionRewardPoints || 0) + achievements.reduce((sum, achievement) => sum + Number(achievement.rewardPoints || 0), 0);\n            const totalReward");
        assertThat(script).doesNotContain("const loadEconomy");
        assertThat(resource("/templates/ui/fragments/date-time.html"))
                .contains("th:fragment=\"dateTime(name, label, hint, required)\"", "type=\"hidden\"",
                        "placeholder=\"dd.mm.yyyy HH:mm\"", "data-date-time-input", "data-date-time-open",
                        "aria-haspopup=\"dialog\"", "data-icon=\"calendar_month\"")
                .doesNotContain("type=\"datetime-local\"", "type=\"date\"", "type=\"time\"");
        assertThat(resource("/static/js/ui/fragments/date-time.js"))
                .contains("(first.getDay() + 6) % 7", "24-hour Material dial", "draft.setHours", "draft.setMinutes",
                        "active.widget.getBoundingClientRect()", "event.composedPath().includes(popup)",
                        "if (active) place()", "parts.dateTab.focus()", "close(false)");
        assertThat(resource("/static/css/ui/fragments/date-time.css"))
                .contains(".uc-datetime-day {", ".uc-clock-number {", "place-items: center;", "padding: 0;", "line-height: 1;");
    }

    /** Every boolean in the admin UI goes through the shared switch, never a bare checkbox. */
    @Test
    void booleansUseTheSharedToggleFragment() throws IOException {
        var template = resource("/templates/ui/admin.html");

        assertThat(resource("/templates/ui/fragments/toggle.html"))
                .contains("th:fragment=\"toggle(name, value, label, hint, checked)\"", "type=\"checkbox\"");
        for (var name : new String[]{"gameTypes", "enabled", "dryRun", "fakeAdmin", "MODERATOR", "ADMIN"})
            assertThat(template).contains("ui/fragments/toggle :: toggle(name='" + name + "'");
        assertThat(template).doesNotContain("type=\"checkbox\"", "admin-check");
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
            assertThat(template).contains("th:if=\"${isFakeAdmin}\"");
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

    @Test
    void keepsTheUiSandboxFrontendOnly() throws IOException {
        try (var templateStream = getClass().getResourceAsStream("/templates/ui/admin-sandbox.html");
             var scriptStream = getClass().getResourceAsStream("/static/js/ui/sandbox.js");
             var durakScriptStream = getClass().getResourceAsStream("/static/js/ui/durak-sandbox.js");
             var liveGameScriptStream = getClass().getResourceAsStream("/static/js/ui/live-game.js");
             var durakGameScriptStream = getClass().getResourceAsStream("/static/js/ui/games/durak.js")) {
            assertThat(templateStream).isNotNull();
            assertThat(scriptStream).isNotNull();
            assertThat(durakScriptStream).isNotNull();
            assertThat(liveGameScriptStream).isNotNull();
            assertThat(durakGameScriptStream).isNotNull();
            var template = new String(templateStream.readAllBytes());
            var script = new String(scriptStream.readAllBytes());
            var durakScript = new String(durakScriptStream.readAllBytes());
            var liveGameScript = new String(liveGameScriptStream.readAllBytes());
            var durakGameScript = new String(durakGameScriptStream.readAllBytes());

            assertThat(script).doesNotContain("fetch(");
            assertThat(durakScript).doesNotContain("fetch(");
            assertThat(script).doesNotContain("/api/admin/sandbox");
            assertThat(durakScript).doesNotContain("/api/admin/sandbox");
            assertThat(script).contains("window.Stomp =");
            assertThat(durakScript).contains("window.Stomp =");
            assertThat(script).contains("window.UltracardsSandboxWagerDeltas", "wagerDeltas:");
            assertThat(durakScript).contains("window.UltracardsSandboxWagerDeltas", "wagerDeltas:");
            assertThat(liveGameScript).contains("state.wagerPayoutAnimated = false;");
            assertThat(durakGameScript).contains("state.wagerPayoutAnimated = false;");
            assertThat(durakGameScript).contains("function startLobbyReturnCountdown(meta) {\n        if (gameEl.dataset.sandbox) return;");
            assertThat(durakScript).contains("/app/game/durak/action");
            assertThat(template).contains("<option value=\"durak\" th:text=\"#{game.durak}\">Durak</option>");
            assertThat(template).contains("id=\"sandbox-hand-cards\"");
            assertThat(template).contains("id=\"sandbox-card-picker\"");
            assertThat(template).contains("id=\"durak-action\"");
            assertThat(template).contains("id=\"durak-hint\" class=\"durak-error\"");
            assertThat(script).contains("function renderHandEditor()");
            assertThat(script).contains("function setHandCard()");
            assertThat(durakScript).contains("getGameConfigDisplayName('Durak'");
            assertThat(durakScript).contains("admin.sandbox.durak.newDeal");
            assertThat(template.indexOf("@{/js/ui/sandbox.js}"))
                    .isLessThan(template.indexOf("@{/js/ui/live-game.js}"));
            assertThat(template.indexOf("@{/js/ui/durak-sandbox.js}"))
                    .isLessThan(template.indexOf("@{/js/ui/games/durak.js}"));
        }
    }

    @Test
    void exposesDurakGameplayAndAdminFrontendContracts() throws IOException {
        try (var gameScriptStream = getClass().getResourceAsStream("/static/js/ui/games/durak.js");
             var sharedGameScriptStream = getClass().getResourceAsStream("/static/js/ui/game.js");
             var liveGameScriptStream = getClass().getResourceAsStream("/static/js/ui/live-game.js");
             var gameStylesStream = getClass().getResourceAsStream("/static/css/ui/games/durak.css");
             var adminScriptStream = getClass().getResourceAsStream("/static/js/ui/admin.js");
             var adminTemplateStream = getClass().getResourceAsStream("/templates/ui/admin.html")) {
            assertThat(gameScriptStream).isNotNull();
            assertThat(sharedGameScriptStream).isNotNull();
            assertThat(liveGameScriptStream).isNotNull();
            assertThat(gameStylesStream).isNotNull();
            assertThat(adminScriptStream).isNotNull();
            assertThat(adminTemplateStream).isNotNull();
            var gameScript = new String(gameScriptStream.readAllBytes());
            var sharedGameScript = new String(sharedGameScriptStream.readAllBytes());
            var liveGameScript = new String(liveGameScriptStream.readAllBytes());
            var gameStyles = new String(gameStylesStream.readAllBytes());
            var adminScript = new String(adminScriptStream.readAllBytes());
            var adminTemplate = new String(adminTemplateStream.readAllBytes());

            assertThat(gameScript).contains("function inferLegalActions()");
            assertThat(gameScript).contains("prev-round-back");
            assertThat(gameScript).contains("prev-round-forward");
            assertThat(gameScript).contains("function seatSlot(index, count)");
            assertThat(gameScript).contains("Math.min(3, 32 / (target - 1))");
            assertThat(gameScript).contains("--seat-fan-rotation");
            assertThat(gameScript).contains("is-dense-player-ring");
            assertThat(gameScript).contains("/app/game/durak/action");
            assertThat(gameScript).contains("function playOnRotate(card)");
            assertThat(gameScript).contains("function playOnFelt(card, slotId)");
            assertThat(gameScript).contains("function targetForCard(card, target)");
            assertThat(gameScript).contains("cardActions(cardCode(card)).includes('ATTACK')");
            assertThat(gameScript).contains("function rotateSlot(ready)");
            assertThat(gameScript).contains("const showsRotate = ()");
            assertThat(gameScript).contains("function hasPassCapacity()");
            assertThat(gameScript).contains("function renderStatusPill()");
            assertThat(gameStyles).contains(".durak-status-name");
            assertThat(gameScript).contains("function armPendingWatchdog(key)");
            assertThat(gameScript).contains("function collectTableCards(cards, finishedBout)");
            assertThat(gameScript).contains("function announceStateChanges(next)");
            assertThat(gameScript).contains("function applyGameNow(game)");
            assertThat(gameStyles).contains(".durak-state-bubble");
            assertThat(gameScript).doesNotContain("dismissRotateSlot");
            assertThat(gameScript).contains("const TURN_WARNING_MS");
            assertThat(liveGameScript).contains("is-turn-running-out");
            assertThat(gameStyles).doesNotContain(".durak-rotate-ghost");
            assertThat(gameScript).contains("instantReject: true");
            assertThat(gameScript).contains("state.pendingFlights.set(key, {");
            assertThat(gameScript).contains("function takePendingTableCard(key, tableClass)");
            assertThat(gameScript).contains("pendingHandRemovals: new Set()");
            assertThat(gameScript).contains("tableConfirmedRemovals: new Set()");
            assertThat(gameScript).contains("state.pendingHandRemovals.add(key)");
            assertThat(gameScript).contains("state.tableConfirmedRemovals.add(key)");
            assertThat(gameScript).contains("function restorePendingCards()");
            assertThat(gameScript).contains("if (reconnecting) resync();");
            assertThat(gameScript).contains("return sendAction('DEFEND', card, slotId)");
            assertThat(gameScript).contains("ui.dealCardsIntoHand(dom.hand, drawn");
            assertThat(gameScript).contains("ui.animateCardBetweenZones({");
            assertThat(gameScript).contains("ui.animateTrickCollect(cards)");
            assertThat(gameScript).contains("const existingCards = new Map");
            assertThat(gameScript).contains("ui.cancelDragCard(state.dragSession)");
            assertThat(sharedGameScript).contains("function cancelAnimations(target)");
            assertThat(sharedGameScript).contains("onInterrupt: done");
            assertThat(sharedGameScript).contains("window.addEventListener('pagehide', cancelActiveDrag)");
            assertThat(sharedGameScript).contains("if (session.cancelled)");
            assertThat(sharedGameScript).contains("!options?.preserveAccepted");
            assertThat(liveGameScript).contains("cancelDragCard(session.handDrag, {remove: true})");
            assertThat(liveGameScript).doesNotContain("returnedCards.forEach");
            assertThat(gameStyles).contains("--seat-fan-distance");
            assertThat(gameStyles).contains("--seat-fan-x");
            assertThat(gameStyles).contains("container-type: size");
            assertThat(gameStyles).contains(".durak-rotate-slot.is-ready");
            assertThat(gameStyles).contains(".durak-attack-card {");
            assertThat(gameStyles).contains(".durak-error.is-visible");
            assertThat(adminScript).doesNotContain("const loadRecordedGames = async");
            assertThat(adminScript).doesNotContain("needsFrontendFilter");
            assertThat(adminScript).contains("const modeLabel =");
            assertThat(adminScript).contains("getGameConfigDisplayName(gameType");
            assertThat(adminScript).contains("syncGamesModeFilter();");
            assertThat(adminTemplate).contains("<option value=\"DURAK\">Durak</option>");
            assertThat(adminTemplate).contains("<option value=\"durak\">Durak</option>");
        }
    }

    private String resource(String path) throws IOException {
        try (var stream = getClass().getResourceAsStream(path)) {
            assertThat(stream).isNotNull();
            return new String(stream.readAllBytes());
        }
    }
}
