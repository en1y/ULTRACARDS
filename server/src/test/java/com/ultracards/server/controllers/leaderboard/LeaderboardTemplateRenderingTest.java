package com.ultracards.server.controllers.leaderboard;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class LeaderboardTemplateRenderingTest {
    @Test
    void includesAccessibleFiltersStatesAndResponsiveAssets() throws IOException {
        try (var templateStream = getClass().getResourceAsStream("/templates/ui/leaderboards.html");
             var scriptStream = getClass().getResourceAsStream("/static/js/ui/leaderboards.js");
             var stylesheetStream = getClass().getResourceAsStream("/static/css/ui/leaderboards.css")) {
            assertThat(templateStream).isNotNull();
            assertThat(scriptStream).isNotNull();
            assertThat(stylesheetStream).isNotNull();
            var template = new String(templateStream.readAllBytes());
            var script = new String(scriptStream.readAllBytes());
            var stylesheet = new String(stylesheetStream.readAllBytes());

            assertThat(template).contains("aria-live=\"polite\"");
            assertThat(template).contains("data-metric=\"GAMES_PLAYED\"");
            assertThat(template).contains("data-metric=\"WIN_RATE\"");
            assertThat(template).contains("id=\"leaderboard-mode\" disabled");
            assertThat(template).contains("id=\"leaderboard-chart-bars\"");
            assertThat(template).contains("leaderboard-intro");
            assertThat(script).contains("credentials: 'same-origin'");
            assertThat(script).contains("replaceChildren(...data.items.map(renderRow))");
            assertThat(script).contains("function renderChart(data)");
            assertThat(script).contains("data.items.slice(0, 10)");
            assertThat(script).contains("state.metric === 'WIN_RATE'");
            assertThat(script).contains("openUserProfilePopup");
            assertThat(stylesheet).contains("@media (max-width: 700px)");
            assertThat(stylesheet).contains(".leaderboard-chart-bars");
            assertThat(stylesheet).contains("height: calc(100% - var(--leaderboard-chart-label-space))");
            assertThat(stylesheet).contains(".leaderboard-player-button");
            assertThat(stylesheet).contains("prefers-reduced-motion");
        }
    }
}
