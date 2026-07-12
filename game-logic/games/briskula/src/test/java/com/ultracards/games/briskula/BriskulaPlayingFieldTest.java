package com.ultracards.games.briskula;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BriskulaPlayingFieldTest {
    @Test
    void rejectedCardDoesNotAdvanceFourCardTurn() {
        var players = new ArrayList<>(List.of(new BriskulaPlayer("one"), new BriskulaPlayer("two")));
        var game = new BriskulaGame(BriskulaGameConfig.TWO_PLAYERS_FOUR_CARDS_IN_HAND_EACH, players);
        game.start();
        var field = game.getPlayingField();
        var player = field.getCurrentPlayer();
        var handSize = player.getHand().getCardsNum();
        var invalidCard = cardNotInHand(player);

        assertThrows(IllegalStateException.class, () -> field.play(invalidCard, player));

        assertTrue(field.getPlayedCards().isEmpty());
        assertSame(player, field.getCurrentPlayer());
        assertEquals(handSize, player.getHand().getCardsNum());
    }

    private BriskulaCard cardNotInHand(BriskulaPlayer player) {
        for (var card : new BriskulaCardFactory().getCards())
            if (!player.getHand().getCards().contains(card)) return card;
        throw new IllegalStateException("Expected at least one card outside the player's hand");
    }
}
