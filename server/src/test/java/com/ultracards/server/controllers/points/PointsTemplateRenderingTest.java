package com.ultracards.server.controllers.points;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class PointsTemplateRenderingTest {
    @Test
    void prerendersThePlayerPointsPageAndOnlyEnhancesItsChart() throws IOException {
        var template = resource("/templates/ui/points.html");
        var script = resource("/static/js/ui/points.js");
        var header = resource("/templates/ui/fragments/header/actions.html");
        var headerLayout = resource("/templates/ui/fragments/header.html");
        var createLobby = resource("/static/js/ui/fragments/createLobby.js");
        var pointsHelper = resource("/static/js/points.js");

        assertThat(template).contains("data-points-balance=${pointsAccount.balance}",
                "th:each=\"achievement : ${pointsAccount.achievements}\"",
                "th:each=\"event : ${pointsEvents}\"",
                "th:each=\"item : ${pointsTransactions.items}\"",
                "signedActivityPoints(${item.amount})",
                "activityPoints(${item.balanceAfter})",
                "#{points.fee.notice(${pointsSettings.wagerFeePercent})}",
                "id=\"points-initial-series\" type=\"application/json\"",
                "/*[[${pointsSeries}]]*/ []");
        assertThat(script).contains("dataset.pointsBalance", "initialSeries = JSON.parse",
                "if (initialSeries) renderPointsChart");
        assertThat(header).contains("data-points-balance th:attr=\"data-points-balance-value=${pointsBalance}\"",
                "th:text=\"${@pointsFormat.compact(pointsBalance)}\">0</strong>&nbsp;");
        assertThat(headerLayout).doesNotContain("ui/fragments/header/points.js");
        assertThat(createLobby).contains("dataset.pointsBalanceValue").doesNotContain("pointsBalance()");
        assertThat(pointsHelper).doesNotContain("fetch('/api/points/balance'");
    }

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
                ".points-symbol,", "color: var(--color-points);",
                // Doubled class so the gold P outranks the delta colours and container rules like
                // `.admin-tile span`; a single class loses to both.
                ".points-symbol.points-symbol { color: var(--color-points); }");
        assertThat(theme).doesNotContain(".points-delta .points-symbol { color: inherit; }",
                ".history-points .points-symbol { color: inherit; }");
        assertThat(pointsPage).contains("pointsDeltaNode(account.changeLast24Hours)",
                "pointActivityNode(pointsDeltaNode(amount))",
                "pointActivityNode(pointsCompactNode(latest.balanceAfter))",
                "trimEnd()}\\u00A0`");
        assertThat(pointsChart).contains("labelTextColor", "change > 0 ? success", "change < 0 ? danger");
        assertThat(admin).contains("pointsDeltaNode(overview.pointsChangeLast7Days)",
                "pointsDeltaNode(user.pointsChangeLast7Days)", "pointsDeltaNode(item.amount, false)");
        // Every Points figure on the overview opens the Points page; they used to land on Users, Games
        // and Lobbies, which read as if the number belonged to that page.
        assertThat(admin).contains("pointsCompactNode(overview.totalPoints), \"/admin/points\"",
                "pointsDeltaNode(overview.pointsChangeLast7Days), \"/admin/points\"",
                "pointsCompactNode(overview.pointsMintedLast7Days), \"/admin/points\"",
                "pointsCompactNode(overview.pointsRakedLast7Days), \"/admin/points\"",
                "pointsCompactNode(overview.pointsEscrowed), \"/admin/points\"",
                "Number(overview.dailyClaimsToday || 0).toLocaleString(), \"/admin/points\"");
        assertThat(search).contains("points: (target) => loadProfilePoints(profile, target)",
                "pointsChangeLast24Hours",
                "/api/points/users/${id}/transactions", "/api/points/users/${id}/series",
                "renderPointsChart", "['1', '1d'], ['3', '3d'], ['7', '7d'], ['30', '30d']",
                "fetch(`/api/points/users/${id}/series?days=3`", "drawChart(series, 3)",
                "createStatTile(t('points.balance'), pointsCompactNode(profile?.points))");
        assertThat(search).doesNotContain("pointsChangeLast7Days", "' / 7d'");
        assertThat(profilePage).contains("getPointsChangeLast24Hours()", "Points in the last 24 hours");
        assertThat(profilePopup).contains("document.addEventListener('dblclick'", ".player-seat .seat-avatar",
                "seat.classList.contains('is-self')", "source: 'game'");
        assertThat(historyCard).contains("value.append(pointsDeltaNode(delta))");
        assertThat(pointsFragment).contains("class=\"points-delta\"", "'is-up'", "'is-down'",
                "th:fragment=\"activityPoints(amount)\"", "th:fragment=\"signedActivityPoints(amount)\"",
                "${text} + '&#160;'");
    }

    @Test
    void showsScheduledEventsAndOmitsEmptyGoalLists() throws IOException {
        var template = resource("/templates/ui/points.html");
        var script = resource("/static/js/ui/points.js");
        var stylesheet = resource("/static/css/ui/points.css");
        var wagerHelper = resource("/static/js/points.js");
        var gameTypes = resource("/static/js/gameTypes.js");
        var eventFragment = resource("/templates/ui/fragments/points.html");

        assertThat(template).contains("id=\"points-events-section\"", "id=\"points-events\"", "id=\"points-fee\"");
        assertThat(template).contains("/css/ui/markdown.css");
        assertThat(eventFragment).contains("event.descriptionHtml", "th:utext=\"${event.descriptionHtml}\"",
                "goal.descriptionHtml", "th:utext=\"${goal.descriptionHtml}\"",
                "points-event-goal-description markdown-body", "event.completedAt", "historyAt");
        assertThat(eventFragment).contains("goal.completed ? '✓' : '✦'", "event.hiddenAchievementCount > 0",
                        "points.events.hiddenAchievements", "points.events.hiddenAchievementHint")
                .doesNotContain("goal.hidden", "points.events.hiddenGoal");
        assertThat(script).contains("eventsSection.hidden = !items.length", "const completeGoals",
                "points-event-progress-track", "points-event-target-track", "points.events.noGoals",
                "aria-valuenow", "item.gameTypes", "item.completed ? '✓' : '✦'",
                "gamesRequired", "winsRequired", "lossesRequired", "drawsRequired",
                "hiddenAchievementCount", "hiddenEventAchievements", "item.gameModes", "gameModeSummary",
                "points.events.requiredSetup", "item.descriptionHtml", "renderTrustedMarkdown",
                "points-event-goal-description markdown-body", "item.completedAt", "historyAt");
        assertThat(script).doesNotContain("points.events.hiddenGoal", "concealed");
        // Events collapse to a summary line; only a live, unfinished event opens itself.
        assertThat(script).contains("createElement('details')", "card.open = item.active && !completed",
                "relativeDateTime");
        // The server supplies one shared order for the pre-rendered and refreshed lists.
        assertThat(script).contains("const active = models.filter(model => model.item.active)",
                "model.item.endsAt && model.past", "const primary = active.length ? active : fallback",
                "const more = models.filter(model => !primary.includes(model))",
                "...primary.map(eventCard)", "moreEvents(more)",
                "points.events.showMore", "points.events.showLess");
        // A finished event reports the date it ended plus what the player took home.
        assertThat(script).contains("points.events.endedOn", "points.events.expired",
                "banked ? 'points.events.earned' : 'points.events.totalReward'",
                "Number(item.earnedPoints || 0)");
        assertThat(script).doesNotContain("points.events.completeBy", "points.events.allComplete",
                "points.events.inProgress");
        assertThat(stylesheet).contains(".points-event > summary", ".points-event-chevron",
                ".points-event-targets", ".points-event.is-complete", ".points-event.is-past",
                ".points-events-more > summary", ".points-events-more-hide",
                ".points-achievement-icon,\n.points-event-goal-state",
                ".points-event-achievement.is-complete .points-event-goal-state",
                "background: var(--color-points);", "color: #201600;",
                "@media (max-width: 560px)");
        assertThat(wagerHelper).contains("/api/points/settings", "data-wager-fee", "data-wager-guide-fee");
        assertThat(gameTypes).contains("filter.jokers && (!filter.deck || filter.deck === '54')");
    }

    @Test
    void showsEndGameWagersAsLargeSeatBubblesWithoutFlyingPoints() throws IOException {
        var script = resource("/static/js/ui/game.js");
        var stylesheet = resource("/static/css/ui/game.css");
        var durakStylesheet = resource("/static/css/ui/games/durak.css");

        assertThat(script).contains("seat-wager-delta", "label.setAttribute('role', 'status')");
        assertThat(script).doesNotContain("window.setTimeout(() => label.remove(), 5200)",
                "wager-settlement-token", "wager-settlement-core", "wager-impact-burst",
                "sourceEl?.classList.contains('is-result')", "--wager-delta-lift");
        assertThat(stylesheet).contains("@keyframes wager-result-bubble-in",
                ".seat-wager-delta .points-symbol { color: var(--color-points); }",
                ".player-seat:has(> .seat-wager-delta)",
                ".player-seat:has(.seat-cards:empty) > .seat-wager-delta",
                "border-top-color: var(--bubble-color)",
                "transform: translateX(-50%)");
        assertThat(stylesheet).doesNotContain("--wager-delta-lift");
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
