package com.ultracards.server.controllers.points;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

/** Pins the v0.4.2 surfaces: the activity graph and the Achievements tab, on both profiles. */
class AchievementTemplateRenderingTest {
    @Test
    void showsTheActivityGraphAndAchievementsTabOnTheProfilePage() throws IOException {
        var page = resource("/templates/ui/profile.html");
        var script = resource("/static/js/ui/profile.js");

        assertThat(page).contains("data-profile-tab=\"achievements\"",
                "data-profile-panel=\"achievements\"",
                "id=\"profile-activity\"",
                "id=\"profile-achievements\"",
                "/js/ui/activity.js");
        // The graph sits on the overview panel, which renders on load rather than on tab change.
        assertThat(page.indexOf("id=\"profile-activity\""))
                .isGreaterThan(page.indexOf("data-profile-panel=\"overview\""))
                .isLessThan(page.indexOf("data-profile-panel=\"stats\""));
        assertThat(script).contains("window.loadActivityGraph?.(document.getElementById('profile-activity'))",
                "window.loadAchievements?.(document.getElementById('profile-achievements'))");
    }

    @Test
    void showsTheActivityGraphAndAchievementsTabInTheHeaderPopup() throws IOException {
        var search = resource("/static/js/ui/fragments/header/search.js");
        var header = resource("/templates/ui/fragments/header.html");

        // activity.js has to load before search.js, which calls into it as a profile renders.
        assertThat(header).contains("/js/ui/activity.js");
        assertThat(header.indexOf("/js/ui/activity.js"))
                .isLessThan(header.indexOf("/js/ui/fragments/header/search.js"));
        assertThat(search).contains("achievements: (target) => window.loadAchievements?.(target, profile?.id)",
                "window.loadActivityGraph?.(activityGraph, profile?.id)",
                "achievements: t('profile.tab.achievements')");
    }

    @Test
    void buildsAMondayFirstYearOfSquaresAgainstTheActivityApi() throws IOException {
        var activity = resource("/static/js/ui/activity.js");
        var stylesheet = resource("/static/css/ui/activity.css");
        var theme = resource("/static/css/theme.css");

        assertThat(activity).contains("const WEEKS = 53",
                // Monday-first per AGENTS.md, and dd.mm.yyyy in the tooltips.
                "(anchor.getDay() + 6) % 7",
                "pad(date.getDate())}.${pad(date.getMonth() + 1)}.${date.getFullYear()}",
                "/api/points/activity?days=",
                "/activity?days=",
                "/api/points/achievements");
        assertThat(stylesheet).contains(".activity-day", "grid-template-rows: repeat(7,");
        // Both surfaces need these styles, so they ship with the theme rather than one page.
        assertThat(theme).contains("@import url(\"./ui/activity.css\");");
    }

    @Test
    void centresTheGraphAndDrivesItsOwnHoverTooltip() throws IOException {
        var activity = resource("/static/js/ui/activity.js");
        var stylesheet = resource("/static/css/ui/activity.css");

        // fit-content + auto margins centre the graph in a wide card; max-width keeps the
        // scroll container working once 53 columns no longer fit.
        assertThat(stylesheet).contains("width: fit-content;", "margin-inline: auto;", "max-width: 100%;")
                .contains(".activity-tooltip");
        // A styled bubble, not the native title — which waits a second and cannot be styled.
        assertThat(activity).contains("cell.dataset.tooltip", "attachTooltip", "role', 'tooltip'")
                .doesNotContain("cell.title =");
        // Delegated from the grid, so 371 squares cost two listeners rather than 742.
        assertThat(activity).contains("grid.addEventListener('mouseover'", "grid.addEventListener('mouseleave'");
    }

    @Test
    void leadsTheAchievementsTabWithTheNextMilestoneAndAScore() throws IOException {
        var activity = resource("/static/js/ui/activity.js");
        var stylesheet = resource("/static/css/ui/activity.css");

        assertThat(activity).contains("renderNextUp", "renderScore", "achievement-next", "achievement-score",
                "achievements.nextUp", "achievements.earnedCount",
                // Ascending target, or the milestone ladder stops reading as a ladder.
                "(Number(left.target) || 0) - (Number(right.target) || 0)");
        assertThat(stylesheet).contains(".achievement-next", ".achievement-score", ".achievement-row",
                ".achievement-mark", ".streak-tile--hero");
    }

    @Test
    void presentsEachAchievementMetricAsAFocusedDraggableMilestoneCarousel() throws IOException {
        var activity = resource("/static/js/ui/activity.js");
        var stylesheet = resource("/static/css/ui/activity.css");

        assertThat(activity).contains("achievement-rail", "centreOnProgress", "scrollToAchievement",
                "nearestAchievement", "selectAchievement", "scheduleRailSettle",
                "attachAchievementRail", "setPointerCapture", "event.pointerType !== 'mouse'",
                "moveAlongRail", "aria-current', 'step'", "event.key === 'ArrowLeft'",
                "achievements.currentGoal", "achievements.position",
                "achievement-rail-control--previous", "achievement-rail-control--next",
                // When a player cleared the whole path, keep its final milestone centred.
                "firstUnearned < 0 ? matching.length - 1 : firstUnearned");
        assertThat(stylesheet).contains("--achievement-card-width", "display: flex;",
                "overflow-x: auto;", "scroll-snap-type: x mandatory;", "scroll-snap-align: center;",
                "scrollbar-width: none;", ".achievement-row.is-selected",
                ".achievement-list.is-dragging", ".achievement-carousel-actions",
                ".achievement-rail-control");
    }

    @Test
    void keepsTheAchievementCarouselInsideTheSearchedProfileDialog() throws IOException {
        var activity = resource("/static/css/ui/activity.css");
        var header = resource("/static/css/ui/fragments/header.css");

        // The popup owns vertical scrolling; the achievement rail alone owns horizontal scrolling.
        assertThat(header).contains(".header-user-profile-content {", "overflow-x: hidden;",
                ".header-user-profile-panel {", "max-width: 100%;");
        // Card sizing is container-relative here. A viewport-relative width overflows a narrow dialog.
        assertThat(activity).contains(".header-user-profile-panel .achievements-view",
                ".header-user-profile-panel .achievement-list",
                "container-type: inline-size;",
                "--achievement-card-width: min(19rem, calc(100cqw - 3.5rem));");
    }

    @Test
    void presentsStreakFreezesAsCentredIceTokens() throws IOException {
        var page = resource("/templates/ui/profile.html");
        var activity = resource("/static/js/ui/activity.js");
        var stylesheet = resource("/static/css/ui/activity.css");

        assertThat(page).contains("profile-achievements-card", "achievement-section-heading",
                "profile-achievements-content");
        assertThat(page.indexOf("profile-achievements-card"))
                .isGreaterThan(page.indexOf("data-profile-panel=\"achievements\""))
                .isLessThan(page.indexOf("id=\"profile-achievements\""));
        assertThat(activity).contains("streak-tile--freezes", "dot.textContent = '❄'",
                "Math.min(freezes, 3)");
        assertThat(stylesheet).contains(".streak-freeze::after", ".streak-freeze.is-held",
                ".streak-tile--freezes", "place-items: center;", "margin-inline: auto;",
                "width: min(100%, 48rem);");
    }

    @Test
    void presentsEveryRewardKindAsADismissibleQueuedCelebration() throws IOException {
        var notifications = resource("/static/js/ui/fragments/header/notifications.js");
        var stylesheet = resource("/static/css/ui/fragments/header.css");

        assertThat(notifications).contains("'ACHIEVEMENT'", "'EVENT_ACHIEVEMENT'", "'EVENT_COMPLETION'",
                "rewardPopupQueue", "createRewardPopup", "dialog.showModal()",
                "reward-celebration-close", "notifications.reward.continue",
                "dialog.addEventListener('cancel'", "markReadIfPossible(notification)");
        assertThat(stylesheet).contains(".reward-celebration", ".reward-celebration::backdrop",
                ".reward-celebration-close", ".reward-kind-achievement",
                ".reward-kind-event-goal", ".reward-kind-event-completion",
                "@media (prefers-reduced-motion: reduce)");
    }

    @Test
    void addsTheRequestedFiftyDayAndLifetimeGameMilestones() throws IOException {
        var migration = resource("/db/migration/V45__add_reward_notifications_and_achievement_milestones.sql");
        assertThat(migration).contains("'STREAK_50'", "'GAMES_250'", "'GAMES_750'",
                "ADD COLUMN reward_points BIGINT");
    }

    /** A count next to a plural noun reads wrong at 1 in all four bundles. */
    @Test
    void keepsCountsAwayFromPluralNouns() throws IOException {
        var activity = resource("/static/js/ui/activity.js");
        assertThat(activity).doesNotContain("games in the last year`", "games (${wins} won)");
    }

    @Test
    void exposesAchievementManagementInTheAdminUi() throws IOException {
        var admin = resource("/templates/ui/admin.html");
        var script = resource("/static/js/ui/admin.js");

        assertThat(admin).contains("data-section=\"achievements\"",
                "@{/admin/achievements}",
                "id=\"admin-achievements-form\"",
                "id=\"admin-streak-form\"",
                // Every field of a streak is correctable, not just the freeze count.
                "name=\"currentStreak\"", "name=\"longestStreak\"", "name=\"lastPlayedDate\"",
                "data-action=\"reset-streak\"",
                "id=\"admin-holders-form\"", "id=\"admin-holders-select\"",
                "id=\"admin-achievement-filter\"", "id=\"admin-achievement-metric\"",
                "id=\"admin-achievement-state\"", "id=\"admin-achievement-sort\"",
                "data-achievement-bulk=\"enable\"", "data-achievement-bulk=\"disable\"");
        assertThat(script).contains("achievements: loadAchievements",
                "request(\"/economy/achievements\"",
                "/economy/streaks/",
                "loadHolders",
                "/users${query({ page: 0, size: 50 })}",
                "applyAchievementView", "dataset.achievementMove", "achievementEditor({");
    }

    /** One search block, reused by every admin screen that has to pick a player. */
    @Test
    void reusesOneUserSearchFragmentAcrossTheAdminScreens() throws IOException {
        var fragment = resource("/templates/ui/fragments/admin/user-search.html");
        var search = resource("/static/js/ui/fragments/admin/user-search.js");
        var admin = resource("/templates/ui/admin.html");
        var script = resource("/static/js/ui/admin.js");

        assertThat(fragment).contains("th:fragment=\"userSearch(name, label, hint)\"",
                "data-user-search-criteria", "data-user-search-results", "data-user-search-exact",
                "data-user-search-browse", "data-user-search-sort", "data-user-search-direction",
                "data-user-search-size", "data-user-search-pagination");
        // Stats, notifications, and streaks all mount the same fragment.
        assertThat(admin).contains("ui/fragments/admin/user-search :: userSearch('stats'",
                "ui/fragments/admin/user-search :: userSearch('notify'",
                "ui/fragments/admin/user-search :: userSearch('streak'",
                "/js/ui/fragments/admin/user-search.js");
        // Criteria are ANDed by the report endpoint, so several apply at once.
        assertThat(search).contains("'username'", "'email'", "'userId'", "'status'", "'role'",
                "params.set(field, value)", "uc:admin-user-picked",
                "params.set('sort'", "params.set('direction'", "formatDate", "data-user-search-next");
        assertThat(script).contains("uc:admin-user-picked");
        // The ad-hoc lookups the fragment replaced are gone.
        assertThat(admin).doesNotContain("id=\"admin-stats-lookup\"", "id=\"admin-streak-lookup\"",
                "admin-user-options");
        assertThat(script).doesNotContain("loadStatsForQuery", "loadStreakForQuery");
    }

    private String resource(String path) throws IOException {
        try (var stream = getClass().getResourceAsStream(path)) {
            assertThat(stream).isNotNull();
            return new String(stream.readAllBytes());
        }
    }
}
