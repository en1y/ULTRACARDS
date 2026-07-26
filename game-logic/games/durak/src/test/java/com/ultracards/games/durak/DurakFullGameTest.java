package com.ultracards.games.durak;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Plays complete games in every supported mode with a deterministic bot and asserts the
 * invariants that no hand-written scenario can cover: card conservation, single-loser or draw
 * results, finish order completeness, and a single game-end notification.
 */
class DurakFullGameTest {

    @Test
    void everySupportedModeCompletesWithConsistentResults() {
        var seed = 0;
        for (var config : DurakGameConfig.validConfigs()) {
            for (int run = 0; run < 3; run++) {
                playAndVerify(config, new Random(seed++));
            }
        }
    }

    private void playAndVerify(DurakGameConfig config, Random random) {
        var players = new ArrayList<DurakPlayer>();
        for (int i = 0; i < config.numberOfPlayers(); i++) {
            players.add(new DurakPlayer("P" + i, i));
        }
        var game = new DurakGame(players, config, random);
        var endings = new int[1];
        game.setGameRecordingHook(new com.ultracards.templates.game.interfaces.GameRecordingHook() {
            @Override
            public void gameEnded(com.ultracards.templates.game.interfaces.GameInterface<?, ?, ?, ?, ?, ?, ?> g,
                                  List<? extends com.ultracards.templates.game.model.AbstractPlayer<?, ?, ?, ?, ?>> winners) {
                endings[0]++;
            }
        });
        game.start();

        var guard = 0;
        while (game.isGameActive()) {
            assertTrue(++guard < 20_000, config.modeKey() + " did not terminate");
            var field = game.getPlayingField();
            var actor = field.getActionPlayer();
            assertTrue(game.activePlayers().contains(actor), "an inactive player was asked to act");
            game.apply(actor, chooseAction(game, field, actor));
        }

        var mode = config.modeKey();
        assertEquals(1, endings[0], mode + ": the game must end exactly once");
        assertEquals(DurakPhase.FINISHED, game.getPlayingField().getPhase(), mode);

        // Nobody vanished: everyone either finished or is the single remaining durak.
        var accounted = new HashSet<>(game.getFinishOrder());
        if (game.isDraw()) {
            assertNull(game.getLoser(), mode);
            assertTrue(game.determineGameWinners().isEmpty(), mode);
        } else {
            assertNotNull(game.getLoser(), mode);
            accounted.add(game.getLoser());
            assertEquals(config.numberOfPlayers() - 1, game.determineGameWinners().size(), mode);
            assertFalse(game.determineGameWinners().contains(game.getLoser()), mode);
        }
        assertEquals(config.numberOfPlayers(), accounted.size(), mode);
        assertEquals(game.getFinishOrder().size(), new HashSet<>(game.getFinishOrder()).size(), mode);

        // Every card is exactly once in a hand, the discard pile, the stock or on the table.
        var seen = new ArrayList<DurakCard>();
        game.getPlayers().forEach(p -> seen.addAll(p.getHand().getCards()));
        seen.addAll(game.getDiscardPile());
        seen.addAll(game.getDeck().getCards());
        if (game.getPlayingField().getOutcome() == null) { // an unresolved table still holds its cards
            seen.addAll(game.getPlayingField().allTableCards());
        }
        assertEquals(config.effectiveCardCount(), seen.size(), mode + ": cards were duplicated or lost");
        assertEquals(config.effectiveCardCount(), new HashSet<>(seen).size(), mode + ": duplicate card");
        if (!config.jokersEnabled()) {
            assertTrue(seen.stream().noneMatch(DurakCard::isJoker), mode + ": a Joker leaked into play");
        }
    }

    /** Defend when possible, otherwise take; throw in whatever is legal, otherwise pass the cursor. */
    private DurakAction chooseAction(DurakGame game, DurakPlayingField field, DurakPlayer actor) {
        return switch (field.getPhase()) {
            case WAITING_FOR_ATTACK -> game.timeoutAction();
            case WAITING_FOR_DEFENSE -> defenceOrTake(game, field, actor);
            default -> throwInOrDone(game, field, actor);
        };
    }

    private DurakAction defenceOrTake(DurakGame game, DurakPlayingField field, DurakPlayer actor) {
        var target = field.uncoveredSlots().getFirst();
        for (var card : List.copyOf(actor.getHand().getCards())) {
            if (game.canBeat(target.attackCard(), card)) {
                return DurakAction.defend(card, target.slotId());
            }
        }
        for (var card : List.copyOf(actor.getHand().getCards())) {
            if (game.canPass(actor, card)) {
                return DurakAction.pass(card);
            }
        }
        return DurakAction.take();
    }

    private DurakAction throwInOrDone(DurakGame game, DurakPlayingField field, DurakPlayer actor) {
        for (var card : List.copyOf(actor.getHand().getCards())) {
            if (game.canThrowIn(card)) {
                return DurakAction.throwIn(card);
            }
        }
        return DurakAction.done();
    }
}
