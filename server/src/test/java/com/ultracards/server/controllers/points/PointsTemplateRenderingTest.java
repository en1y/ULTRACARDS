package com.ultracards.server.controllers.points;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class PointsTemplateRenderingTest {
    @Test
    void rendersThePolishedRewardAchievementsAndIconPagination() throws IOException {
        try (var templateStream = getClass().getResourceAsStream("/templates/ui/points.html");
             var scriptStream = getClass().getResourceAsStream("/static/js/ui/points.js");
             var stylesheetStream = getClass().getResourceAsStream("/static/css/ui/points.css")) {
            assertThat(templateStream).isNotNull();
            assertThat(scriptStream).isNotNull();
            assertThat(stylesheetStream).isNotNull();
            var template = new String(templateStream.readAllBytes());
            var script = new String(scriptStream.readAllBytes());
            var stylesheet = new String(stylesheetStream.readAllBytes());

            assertThat(template).contains("points-daily-reward");
            assertThat(template).contains("previous_round.svg", "next_round.svg");
            assertThat(template).doesNotContain(">Previous</button>", ">Next</button>");
            assertThat(script).contains("formatDateTime", ".${date.getFullYear()}");
            assertThat(script).contains("points-achievement-track");
            assertThat(stylesheet).contains("grid-template-columns: repeat(2, minmax(0, 1fr))");
            assertThat(stylesheet).contains("@media (max-width: 520px)");
            assertThat(stylesheet).doesNotContain("gradient");
        }
    }

    @Test
    void usesOneSignedColorContractAcrossPointsFragments() throws IOException {
        var helper = resource("/static/js/points.js");
        var theme = resource("/static/css/theme.css");
        var pointsPage = resource("/static/js/ui/points.js");
        var pointsChart = resource("/static/js/points-chart.js");
        var admin = resource("/static/js/ui/admin.js");
        var search = resource("/static/js/ui/fragments/header/search.js");
        var profilePopup = resource("/static/js/ui/fragments/header/profile-popup.js");
        var profilePage = resource("/templates/ui/profile.html");
        var historyCard = resource("/static/js/ui/fragments/history-card.js");
        var pointsFragment = resource("/templates/ui/fragments/points.html");

        assertThat(helper).contains("window.pointsDeltaNode", "number > 0 ? 'is-up'", "number < 0 ? 'is-down'");
        assertThat(theme).contains(".points-delta.is-up { color: var(--color-success); }",
                ".points-delta.is-down { color: var(--color-danger); }",
                ".points-symbol,", "color: var(--color-points);");
        assertThat(theme).doesNotContain(".points-delta .points-symbol { color: inherit; }",
                ".history-points .points-symbol { color: inherit; }");
        assertThat(pointsPage).contains("pointsDeltaNode(account.changeLast24Hours)", "pointsDeltaNode(amount, false)");
        assertThat(pointsChart).contains("labelTextColor", "change > 0 ? success", "change < 0 ? danger");
        assertThat(admin).contains("pointsDeltaNode(overview.pointsChangeLast7Days)",
                "pointsDeltaNode(user.pointsChangeLast7Days)", "pointsDeltaNode(item.amount, false)");
        assertThat(search).contains("pointsButton", "pointsChangeLast24Hours",
                "/api/points/users/${id}/transactions", "/api/points/users/${id}/series",
                "renderPointsChart", "['1', '1d'], ['3', '3d'], ['7', '7d'], ['30', '30d']",
                "fetch(`/api/points/users/${id}/series?days=3`", "drawChart(series, 3)",
                "createStatTile(t('points.balance'), pointsCompactNode(profile?.points))");
        assertThat(search).doesNotContain("pointsChangeLast7Days", "' / 7d'");
        assertThat(profilePage).contains("getPointsChangeLast24Hours()", "Points in the last 24 hours");
        assertThat(profilePopup).contains("document.addEventListener('dblclick'", ".player-seat .seat-avatar",
                "seat.classList.contains('is-self')", "source: 'game'");
        assertThat(historyCard).contains("value.append(pointsDeltaNode(delta))");
        assertThat(pointsFragment).contains("class=\"points-delta\"", "'is-up'", "'is-down'");
    }

    @Test
    void showsScheduledEventsAndOmitsEmptyGoalLists() throws IOException {
        var template = resource("/templates/ui/points.html");
        var script = resource("/static/js/ui/points.js");
        var wagerHelper = resource("/static/js/points.js");

        assertThat(template).contains("id=\"points-events-section\"", "id=\"points-events\"", "id=\"points-fee\"");
        assertThat(script).contains("eventsSection.hidden = !items.length", "if (item.achievements?.length)",
                "gamesRequired", "winsRequired", "lossesRequired", "drawsRequired");
        assertThat(wagerHelper).contains("/api/points/settings", "data-wager-fee", "data-wager-guide-fee");
    }

    @Test
    void showsEndGameWagersAsLargeSeatBubblesWithoutFlyingPoints() throws IOException {
        var script = resource("/static/js/ui/game.js");
        var stylesheet = resource("/static/css/ui/game.css");
        var durakStylesheet = resource("/static/css/ui/games/durak.css");

        assertThat(script).contains("seat-wager-delta", "label.setAttribute('role', 'status')");
        assertThat(script).doesNotContain("window.setTimeout(() => label.remove(), 5200)",
                "wager-settlement-token", "wager-settlement-core", "wager-impact-burst");
        assertThat(stylesheet).contains("@keyframes wager-result-bubble-in",
                ".seat-wager-delta .points-symbol { color: var(--color-points); }",
                ".player-seat:has(.seat-cards:empty) > .seat-wager-delta",
                "border-top-color: var(--bubble-color)");
        assertThat(stylesheet).contains("font-size: clamp(1rem, 2.6vmin, 1.3rem)");
        assertThat(stylesheet).doesNotContain("@keyframes wager-token-flight", "@keyframes wager-core-release");
        assertThat(durakStylesheet).contains("html[data-game-ui=\"fullscreen\"] .durak-game-layout .player-summary {",
                "position: relative;");
    }

    private String resource(String path) throws IOException {
        try (var stream = getClass().getResourceAsStream(path)) {
            assertThat(stream).isNotNull();
            return new String(stream.readAllBytes());
        }
    }
}
