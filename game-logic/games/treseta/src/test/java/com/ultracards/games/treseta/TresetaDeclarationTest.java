package com.ultracards.games.treseta;

import com.ultracards.cards.ItalianCardSuit;
import com.ultracards.cards.ItalianCardValue;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TresetaDeclarationTest {

    @Test
    void infersOfAKindAndNapolitanaFromCards() {
        var threeAces = cards(ItalianCardValue.ACE, ItalianCardSuit.BASTONI, ItalianCardSuit.COPPE, ItalianCardSuit.SPADE);
        var declaration = TresetaDeclaration.fromCards(threeAces);
        assertEquals(TresetaDeclaration.Type.ACES, declaration.type());
        assertEquals(9, declaration.getPoints());

        var fourTwos = cards(ItalianCardValue.TWO, ItalianCardSuit.values());
        assertEquals(12, TresetaDeclaration.fromCards(fourTwos).getPoints());

        var napolitana = TresetaDeclaration.fromCards(List.of(
                new TresetaCard(ItalianCardSuit.COPPE, ItalianCardValue.ACE),
                new TresetaCard(ItalianCardSuit.COPPE, ItalianCardValue.TWO),
                new TresetaCard(ItalianCardSuit.COPPE, ItalianCardValue.THREE)));
        assertEquals(TresetaDeclaration.Type.NAPOLITANA, napolitana.type());
        assertEquals(9, napolitana.getPoints());
    }

    @Test
    void rejectsInvalidCardSelections() {
        assertThrows(IllegalArgumentException.class, () -> TresetaDeclaration.fromCards(List.of()));
        // kings are not declarable
        assertThrows(IllegalArgumentException.class, () -> TresetaDeclaration.fromCards(
                cards(ItalianCardValue.KING, ItalianCardSuit.BASTONI, ItalianCardSuit.COPPE, ItalianCardSuit.SPADE)));
        // mixed values that are not a napolitana
        assertThrows(IllegalArgumentException.class, () -> TresetaDeclaration.fromCards(List.of(
                new TresetaCard(ItalianCardSuit.COPPE, ItalianCardValue.ACE),
                new TresetaCard(ItalianCardSuit.COPPE, ItalianCardValue.TWO),
                new TresetaCard(ItalianCardSuit.COPPE, ItalianCardValue.FOUR))));
    }

    @Test
    void playerDeclaresOnlyBeforeFirstPlayedCardAndWithoutRepeats() {
        var player = new TresetaPlayer("Tester");
        var hand = new TresetaHand(10);
        for (var suit : ItalianCardSuit.values()) hand.addCard(new TresetaCard(suit, ItalianCardValue.ACE));
        hand.addCard(new TresetaCard(ItalianCardSuit.COPPE, ItalianCardValue.TWO));
        hand.addCard(new TresetaCard(ItalianCardSuit.COPPE, ItalianCardValue.THREE));
        player.setHand(hand);

        var napolitana = new TresetaDeclaration(TresetaDeclaration.Type.NAPOLITANA, Set.of(ItalianCardSuit.COPPE));
        player.declare(napolitana);
        // same cards, different declaration -> allowed
        player.declare(TresetaDeclaration.fromCards(
                cards(ItalianCardValue.ACE, ItalianCardSuit.BASTONI, ItalianCardSuit.COPPE, ItalianCardSuit.SPADE)));
        // The same value with a different three-card set is also a different declaration.
        player.declare(TresetaDeclaration.fromCards(
                cards(ItalianCardValue.ACE, ItalianCardSuit.BASTONI, ItalianCardSuit.COPPE, ItalianCardSuit.DENARI)));
        // Four cards upgrade and replace every three-card declaration of that value.
        player.declare(TresetaDeclaration.fromCards(cards(ItalianCardValue.ACE, ItalianCardSuit.values())));
        assertEquals(2, player.getDeclarations().size());
        assertEquals(4, player.getDeclarations().get(1).suits().size());
        // exact same declaration -> rejected
        assertThrows(IllegalArgumentException.class, () -> player.declare(napolitana));
        // cards not in hand -> rejected
        assertThrows(IllegalArgumentException.class, () -> player.declare(TresetaDeclaration.fromCards(
                cards(ItalianCardValue.TWO, ItalianCardSuit.BASTONI, ItalianCardSuit.COPPE, ItalianCardSuit.SPADE))));

        player.playCard(new TresetaCard(ItalianCardSuit.COPPE, ItalianCardValue.TWO));
        assertThrows(IllegalArgumentException.class, () -> player.declare(
                new TresetaDeclaration(TresetaDeclaration.Type.THREES,
                        Set.of(ItalianCardSuit.BASTONI, ItalianCardSuit.COPPE, ItalianCardSuit.SPADE))));
    }

    @Test
    void playerCanNotDeclareFourThenThreeOfTheSameValue() {
        var player = new TresetaPlayer("Tester");
        var hand = new TresetaHand(10);
        for (var suit : ItalianCardSuit.values()) hand.addCard(new TresetaCard(suit, ItalianCardValue.ACE));
        player.setHand(hand);

        player.declare(TresetaDeclaration.fromCards(cards(ItalianCardValue.ACE, ItalianCardSuit.values())));

        assertThrows(IllegalArgumentException.class, () -> player.declare(TresetaDeclaration.fromCards(
                cards(ItalianCardValue.ACE, ItalianCardSuit.BASTONI, ItalianCardSuit.COPPE, ItalianCardSuit.SPADE))));
    }

    @Test
    void gameRejectsDeclarationsInModesWithoutThem() {
        var players = new ArrayList<TresetaPlayer>();
        for (int i = 0; i < 2; i++) players.add(new TresetaPlayer("Player " + (i + 1)));
        var game = new TresetaGame(players, TresetaGameConfig.TWO_PLAYERS);
        game.start();

        var declarer = players.get(0);
        var hand = new TresetaHand(10);
        for (var suit : ItalianCardSuit.values()) hand.addCard(new TresetaCard(suit, ItalianCardValue.THREE));
        declarer.setHand(hand);

        assertThrows(IllegalArgumentException.class, () -> game.declare(declarer,
                TresetaDeclaration.fromCards(cards(ItalianCardValue.THREE, ItalianCardSuit.values()))));
        assertEquals(0, declarer.getPoints());
    }

    @Test
    void gameAddsDeclarationPointsToDeclarerAndTeammate() {
        var players = new ArrayList<TresetaPlayer>();
        for (int i = 0; i < 4; i++) players.add(new TresetaPlayer("Player " + (i + 1)));
        var game = new TresetaGame(players, TresetaGameConfig.FOUR_PLAYERS_WITH_TEAMS_WITH_DECLARATIONS);
        game.start();

        var declarer = players.getFirst();
        var hand = new TresetaHand(10);
        for (var suit : ItalianCardSuit.values()) hand.addCard(new TresetaCard(suit, ItalianCardValue.THREE));
        declarer.setHand(hand);

        game.declare(declarer, TresetaDeclaration.fromCards(
                cards(ItalianCardValue.THREE, ItalianCardSuit.BASTONI, ItalianCardSuit.COPPE, ItalianCardSuit.SPADE)));

        assertEquals(9, players.get(0).getPoints());
        assertEquals(9, players.get(2).getPoints());

        game.declare(declarer, TresetaDeclaration.fromCards(
                cards(ItalianCardValue.THREE, ItalianCardSuit.BASTONI, ItalianCardSuit.COPPE, ItalianCardSuit.DENARI)));

        assertEquals(18, players.get(0).getPoints());
        assertEquals(18, players.get(2).getPoints());

        game.declare(declarer, TresetaDeclaration.fromCards(cards(ItalianCardValue.THREE, ItalianCardSuit.values())));

        assertEquals(12, players.get(0).getPoints());
        assertEquals(12, players.get(2).getPoints());
        assertEquals(0, players.get(1).getPoints());
        assertEquals(0, players.get(3).getPoints());
        assertEquals(1, declarer.getDeclarations().size());
        assertEquals(4, declarer.getDeclarations().getFirst().suits().size());

        assertThrows(IllegalArgumentException.class, () -> game.declare(declarer, TresetaDeclaration.fromCards(
                cards(ItalianCardValue.THREE, ItalianCardSuit.BASTONI, ItalianCardSuit.COPPE, ItalianCardSuit.SPADE))));
        assertEquals(12, players.get(0).getPoints());
        assertEquals(12, players.get(2).getPoints());
    }

    @Test
    void gameOnlyAllowsDeclarationsOnThePlayersFirstTurn() {
        var players = new ArrayList<TresetaPlayer>();
        for (int i = 0; i < 2; i++) players.add(new TresetaPlayer("Player " + (i + 1)));
        var game = new TresetaGame(players, TresetaGameConfig.TWO_PLAYERS_WITH_DECLARATIONS);
        game.start();

        var secondPlayerHand = new TresetaHand(10);
        for (var suit : ItalianCardSuit.values()) secondPlayerHand.addCard(new TresetaCard(suit, ItalianCardValue.ACE));
        players.get(1).setHand(secondPlayerHand);

        assertThrows(IllegalArgumentException.class, () -> game.declare(players.get(1),
                TresetaDeclaration.fromCards(cards(ItalianCardValue.ACE, ItalianCardSuit.values()))));
    }

    private List<TresetaCard> cards(ItalianCardValue value, ItalianCardSuit... suits) {
        var cards = new ArrayList<TresetaCard>();
        for (var suit : suits) cards.add(new TresetaCard(suit, value));
        return cards;
    }
}
