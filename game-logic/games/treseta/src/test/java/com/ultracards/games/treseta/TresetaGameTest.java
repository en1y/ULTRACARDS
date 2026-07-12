package com.ultracards.games.treseta;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TresetaGameTest {

    @Test
    void acceptsEachSupportedConfiguration() {
        for (var config : TresetaGameConfig.values()) {
            assertDoesNotThrow(() -> new TresetaGame(players(config), config));
        }
    }

    @Test
    void rejectsConfigurationWithWrongPlayerCount() {
        assertThrows(IllegalArgumentException.class,
                () -> new TresetaGame(players(TresetaGameConfig.TWO_PLAYERS, 3), TresetaGameConfig.TWO_PLAYERS));
    }

    @Test
    void rejectedCardDoesNotMutatePlayingField() {
        var game = new TresetaGame(players(TresetaGameConfig.TWO_PLAYERS), TresetaGameConfig.TWO_PLAYERS);
        game.start();
        var field = game.getPlayingField();
        var player = field.getCurrentPlayer();
        var handSize = player.getHand().getCardsNum();
        var invalidCard = cardNotInHand(player);

        assertThrows(IllegalStateException.class, () -> field.play(invalidCard, player));

        assertTrue(field.getPlayedCards().isEmpty());
        assertFalse(field.getHasPlayerPlayed().get(player));
        assertSame(player, field.getCurrentPlayer());
        assertEquals(handSize, player.getHand().getCardsNum());
    }

    @Test
    void returnsAllTiedWinnersWithoutTeams() {
        var players = players(TresetaGameConfig.THREE_PLAYERS);
        players.get(0).setPoints(12);
        players.get(1).setPoints(12);
        players.get(2).setPoints(11);
        var game = new TresetaGame(players, TresetaGameConfig.THREE_PLAYERS);

        assertEquals(List.of(players.get(0), players.get(1)), game.determineGameWinners());
    }

    private ArrayList<TresetaPlayer> players(TresetaGameConfig config) {
        return players(config, config.getNumberOfPlayers());
    }

    private ArrayList<TresetaPlayer> players(TresetaGameConfig config, int playerCount) {
        var players = new ArrayList<TresetaPlayer>();
        for (int i = 0; i < playerCount; i++) {
            players.add(new TresetaPlayer("Player " + (i + 1)));
        }
        return players;
    }

    private TresetaCard cardNotInHand(TresetaPlayer player) {
        for (var card : new TresetaCardFactory().getCards())
            if (!player.getHand().getCards().contains(card)) return card;
        throw new IllegalStateException("Expected at least one card outside the player's hand");
    }
}
