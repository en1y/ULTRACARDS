package com.ultracards.ui.controllers;

import com.ultracards.games.briskula.BriskulaGameConfig;
import com.ultracards.games.treseta.TresetaGameConfig;
import org.junit.jupiter.api.Test;
import org.springframework.ui.ConcurrentModel;

import javax.imageio.ImageIO;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GuideUIControllerTest {
    private final GuideUIController controller = new GuideUIController();

    @Test
    void interactiveGuidesExposeEveryRealGameModeToAnonymousPlayers() {
        var briskulaModel = new ConcurrentModel();
        assertEquals("ui/guides/briskula", controller.briskula(null, briskulaModel));
        assertEquals("1", briskulaModel.getAttribute("currentUserId"));
        assertArrayEquals(BriskulaGameConfig.values(),
                (BriskulaGameConfig[]) briskulaModel.getAttribute("guideModes"));

        var tresetaModel = new ConcurrentModel();
        assertEquals("ui/guides/treseta", controller.treseta(null, tresetaModel));
        assertArrayEquals(TresetaGameConfig.values(),
                (TresetaGameConfig[]) tresetaModel.getAttribute("guideModes"));
    }

    @Test
    void pokerAndDurakRemainPublicPlaceholderPages() {
        var pokerModel = new ConcurrentModel();
        assertEquals("ui/guides/coming-soon", controller.poker(null, pokerModel));
        assertEquals("Poker", pokerModel.getAttribute("gameName"));

        var durakModel = new ConcurrentModel();
        assertEquals("ui/guides/coming-soon", controller.durak(null, durakModel));
        assertEquals("Durak", durakModel.getAttribute("gameName"));
    }

    @Test
    void simulatorUsesPresetTeachingHandsAndServerRenderedModes() throws IOException {
        var script = resource("/static/js/ui/guides/guide-game.js");
        assertTrue(script.contains("const PRESET_HANDS"));
        assertTrue(script.contains("['D1', 'B6', 'C2', 'S3']"));
        assertTrue(script.contains("['S2', 'S3', 'S1', 'C4']"));
        assertTrue(script.contains(".concat('D4')"));
        assertTrue(script.contains("guides.controls.explainer"));
        assertTrue(script.contains("guides.play.trumpExplain"));
        assertTrue(script.contains("guides.play.declarationWhat"));
        assertTrue(script.contains("'/app/game/declare'"));
        assertTrue(script.contains("function coachPoints(points)"));
        assertTrue(script.contains("function previousIntro()"));
        assertTrue(script.contains("setControls('guides.interactive.restart')"));
        // The round recap waits for Next, and Previous replays your last turn.
        assertTrue(script.contains("setControls('guides.coach.next')"));
        assertTrue(script.contains("function replayMove()"));
        assertTrue(script.contains("undoState = structuredClone(state)"));
        // The coach flies to what it explains, so there is no arrow overlay left.
        assertTrue(script.contains("function positionCoach()"));
        assertTrue(script.contains("guide-coach-target"));
        assertTrue(script.contains("'#player-summary'"));
        // Centre stage + board locked for messages; gone entirely while you play.
        assertTrue(script.contains("(board.width - w) / 2"));
        assertTrue(script.contains("function setModal(on)"));
        assertTrue(script.contains("setModal(false)"));
        assertTrue(script.contains("puckOpen = !puckOpen"));
        // The mascot rides the table's corner, not the board's empty corner.
        assertTrue(script.contains("'.table-felt, .table-surface'"));
        assertTrue(script.contains("--puck-x"));
        // Treseta has no trump, so an empty deck must not talk about one.
        assertTrue(script.contains("type === 'briskula' ? 'guides.play.trumpGone' : 'guides.play.deckEmpty'"));
        // No dimming before the demo starts, and no dead Previous button.
        assertTrue(script.contains("phase === 'intro'"));
        assertTrue(script.contains("previousBtn.hidden = !(phase === 'intro' ? introStep > 0 : undoState)"));
        assertFalse(script.contains("previousBtn.disabled"));
        // Position is solved in board coordinates, so scrolling needs no listener.
        assertFalse(script.contains("addEventListener('scroll'"));
        assertFalse(script.contains("guide-arrow"));
        assertFalse(script.contains("guides.coach.playThis"));
        assertFalse(script.contains("guide-restart"));
        assertFalse(script.contains("subtree: true, attributes: true"));
        assertFalse(script.contains("behavior: 'smooth'"));
        assertFalse(script.contains("Math.random"));

        var styles = resource("/static/css/ui/guides.css");
        assertTrue(styles.contains(".guide-layout .table-surface"));
        assertTrue(styles.contains(".guide-layout .player-summary"));
        assertTrue(styles.contains("position: absolute"));
        assertTrue(styles.contains(".guide-coach.tail-top"));
        assertTrue(styles.contains("--coach-x"));
        // The scrim dims the whole page, not just the board box.
        assertTrue(styles.contains(".guide-coach-scrim { position: fixed; inset: 0;"));
        assertTrue(styles.contains("left: var(--puck-x, .75rem); top: var(--puck-y, .75rem)"));
        // No transition before the first placement, or it flies in from 0,0.
        assertTrue(styles.contains(".guide-coach.can-fly"));
        assertTrue(styles.contains("position: absolute; top: 0; left: 0; z-index: 2010"));
        // The sticky header outranks every coach layer, so nothing spills over it.
        assertTrue(styles.contains(".uc-header { z-index: 2100; }"));
        assertTrue(styles.contains("prefers-reduced-motion"));
        assertFalse(styles.contains(".guide-arrow"));
        assertFalse(styles.contains(".guide-coach-bar"));
        assertFalse(styles.contains("width: min(18rem"));
        assertFalse(styles.contains("gradient"));

        var briskula = resource("/templates/ui/guides/briskula.html");
        assertTrue(briskula.contains("th:each=\"mode : ${guideModes}\""));
        assertTrue(briskula.contains("ui/fragments/guides/coach :: coach"));
        // Mode picker belongs to the page copy, not floating over the table.
        assertTrue(briskula.indexOf("guide-mode-field") < briskula.indexOf("game-layout"));
        assertTrue(briskula.contains("th:href=\"@{/guides}\""));
        // The coach floats on every screen size now: no sidebar, no mobile dock.
        assertFalse(briskula.contains("guide-coach-bar"));
        assertFalse(briskula.contains("mobile-chat-toggle"));
        assertFalse(briskula.contains("mobileChatDock.js"));

        var treseta = resource("/templates/ui/guides/treseta.html");
        assertTrue(treseta.contains("th:each=\"mode : ${guideModes}\""));
        assertTrue(treseta.contains("ui/fragments/guides/coach :: coach"));
        // Mode picker belongs to the page copy, not floating over the table.
        assertTrue(treseta.indexOf("guide-mode-field") < treseta.indexOf("game-layout"));
        assertTrue(treseta.contains("th:href=\"@{/guides}\""));
        // The coach floats on every screen size now: no sidebar, no mobile dock.
        assertFalse(treseta.contains("guide-coach-bar"));
        assertFalse(treseta.contains("mobile-chat-toggle"));
        assertFalse(treseta.contains("mobileChatDock.js"));

        var coach = resource("/templates/ui/fragments/guides/coach.html");
        assertTrue(coach.contains("id=\"guide-coach-bubble\""));
        assertTrue(coach.contains("id=\"guide-previous\""));
        assertTrue(coach.contains("id=\"guide-primary\""));
        assertTrue(coach.contains("guide-coach-tail"));
        assertTrue(coach.contains("id=\"guide-coach-scrim\""));
        assertTrue(coach.contains("id=\"guide-coach-puck\""));
        assertTrue(coach.contains("id=\"guide-hide\""));
        // The mascot is a themed icon file like every other uc-icon, used twice.
        assertEquals(2, coach.split("data-icon=\"guide_coach\"", -1).length - 1);
        assertEquals(2, coach.split("data-icon-extension=\"png\"", -1).length - 1);
        assertFalse(coach.contains("<svg"));
        for (var folder : new String[] {"light", "dark"}) {
            try (var stream = getClass().getResourceAsStream("/static/pics/" + folder + "/guide_coach.png")) {
                var mark = ImageIO.read(stream);
                assertEquals(256, mark.getWidth(), folder);
                assertEquals(256, mark.getHeight(), folder);
                assertTrue(mark.getColorModel().hasAlpha(), folder);
            }
        }
        assertTrue(resource("/static/js/theme.js").contains("img.dataset.iconExtension || 'svg'"));

        var header = resource("/static/js/ui/fragments/header/layout.js");
        assertFalse(header.contains("addEventListener('wheel'"));
    }

    private String resource(String path) throws IOException {
        try (var stream = getClass().getResourceAsStream(path)) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
