package com.ultracards.server.controllers.lobby;

import com.ultracards.server.service.points.PointsService;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class LobbyWagerTemplateRenderingTest {
    /**
     * The bet slider walks a ladder defined in points.js while the server rejects
     * anything outside MIN_STAKE..MAX_STAKE. Nothing links the two, so a change to
     * either side silently produces a slider whose ends the server refuses to save.
     */
    @Test
    void theBetLadderEndsWhereTheServerStakeBoundsDo() throws IOException {
        try (var ladderStream = getClass().getResourceAsStream("/static/js/points.js")) {
            assertThat(ladderStream).isNotNull();
            var ladder = new String(ladderStream.readAllBytes());

            assertThat(ladder).contains("const steps = [%s,".formatted(literal(PointsService.MIN_STAKE)));
            assertThat(ladder).contains(".concat(%s);".formatted(literal(PointsService.MAX_STAKE)));
            assertThat(ladder).contains("const ticks = [%s,".formatted(literal(PointsService.MIN_STAKE)));
            assertThat(ladder).contains(", %s];".formatted(literal(PointsService.MAX_STAKE)));
        }
    }

    /** The same number the way JavaScript spells it, digit separators and all. */
    private static String literal(long value) {
        return String.format(java.util.Locale.ROOT, "%,d", value).replace(',', '_');
    }

    @Test
    void keepsTheCreationWagerCompactAndHorizontal() throws IOException {
        try (var templateStream = getClass().getResourceAsStream("/templates/ui/fragments/create-lobby.html");
             var stylesheetStream = getClass().getResourceAsStream("/static/css/ui/fragments/create-lobby.css")) {
            assertThat(templateStream).isNotNull();
            assertThat(stylesheetStream).isNotNull();
            var template = new String(templateStream.readAllBytes());
            var stylesheet = new String(stylesheetStream.readAllBytes());

            assertThat(template).contains("#{points.wager.title}");
            assertThat(template).doesNotContain("#{points.wager.optional}");
            // The bet is a ladder slider now: no free-text amount, no hard-coded bounds.
            assertThat(template).doesNotContain("create-wager-stake");
            assertThat(template).contains("data-wager-amount");
            assertThat(template).contains("class=\"wager-ticks\"");
            assertThat(template).contains("data-wager-preset=\"%d\"".formatted(PointsService.MAX_STAKE));
            assertThat(stylesheet).contains(".create-lobby-wager {\n    display: contents;");
            assertThat(stylesheet).contains("grid-column: 1 / -1");
            assertThat(stylesheet).contains("grid-template-columns: repeat(2, minmax(0, 1fr))");
        }
    }

    @Test
    void quotesTheFeeForEveryCreateLobbyRuleChange() throws IOException {
        var gameTypes = resource("/static/js/gameTypes.js");
        var createLobby = resource("/static/js/ui/fragments/createLobby.js");

        assertThat(gameTypes).contains("`${mode}_WITH_DECLARATIONS`", "config.declarationsEnabled");
        assertThat(createLobby).contains("settingsElement.addEventListener('change'", "syncCreateState();",
                "resolveGameConfigKey(gameType, config)");
    }

    @Test
    void jokerFiltersAlwaysSelectTheOnlyValidDeck() throws IOException {
        assertThat(resource("/static/js/gameTypes.js")).contains(
                "if (chosen.jokers === 'true') chosen.deck = '54';",
                "jokers.addEventListener('change'",
                "setDurakChoice(deck, '54')",
                "deck.addEventListener('change'",
                "setDurakChoice(jokers, DURAK_FILTER_ANY)");
    }

    private String resource(String path) throws IOException {
        try (var stream = getClass().getResourceAsStream(path)) {
            assertThat(stream).isNotNull();
            return new String(stream.readAllBytes());
        }
    }
}
