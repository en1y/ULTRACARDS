package com.ultracards.games.durak;

import com.ultracards.cards.PokerCardSuit;
import com.ultracards.cards.PokerCardValue;
import com.ultracards.templates.game.exceptions.DeckException;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.random.RandomGenerator;

import static org.junit.jupiter.api.Assertions.*;

class DurakDeckTest {

    private static DurakGameConfig config(int players, int deck, boolean jokers) {
        return new DurakGameConfig(players, deck, jokers, DurakThrowInPolicy.NEIGHBORS_ONLY, false);
    }

    @Test
    void shortPackHoldsNineThroughAce() {
        var pack = new DurakCardFactory().createPack(config(2, 24, false));
        assertEquals(24, pack.size());
        assertTrue(pack.stream().allMatch(c -> c.rank() >= 9));
        assertEquals(6, pack.stream().map(DurakCard::rank).distinct().count());
    }

    @Test
    void mediumPackHoldsSixThroughAce() {
        var pack = new DurakCardFactory().createPack(config(2, 36, false));
        assertEquals(36, pack.size());
        assertTrue(pack.stream().allMatch(c -> c.rank() >= 6));
        assertEquals(9, pack.stream().map(DurakCard::rank).distinct().count());
    }

    @Test
    void fullPackWithoutJokersIsFiftyTwoSuitedCards() {
        var pack = new DurakCardFactory().createPack(config(2, 54, false));
        assertEquals(52, pack.size());
        assertTrue(pack.stream().noneMatch(DurakCard::isJoker));
    }

    @Test
    void fullPackWithJokersAddsTwoDistinctJokers() {
        var pack = new DurakCardFactory().createPack(config(2, 54, true));
        assertEquals(54, pack.size());
        var jokers = pack.stream().filter(DurakCard::isJoker).toList();
        assertEquals(2, jokers.size());
        assertNotEquals(jokers.get(0), jokers.get(1));
        assertTrue(jokers.stream().anyMatch(DurakCard::isRed));
        assertTrue(jokers.stream().anyMatch(DurakCard::isBlack));
    }

    @Test
    void packsHaveNoDuplicateCodes() {
        for (var cfg : DurakGameConfig.validConfigs()) {
            var pack = new DurakCardFactory().createPack(cfg);
            var codes = new HashSet<String>();
            pack.forEach(c -> assertTrue(codes.add(c.code()), c.code() + " duplicated in " + cfg.modeKey()));
            assertEquals(cfg.effectiveCardCount(), pack.size(), cfg.modeKey());
        }
    }

    @Test
    void shufflingNeverLeavesAJokerAsTheTrumpIndicator() {
        var cfg = config(6, 54, true);
        for (int seed = 0; seed < 200; seed++) {
            var deck = DurakDeck.shuffled(cfg, RandomGenerator.getDefault());
            assertFalse(deck.getCards().getLast().isJoker());
            assertEquals(54, deck.getSize());
        }
    }

    @Test
    void injectedOrderIsHonoured() {
        var cfg = config(2, 24, false);
        var pack = new ArrayList<>(new DurakCardFactory().createPack(cfg));
        var deck = DurakDeck.ordered(cfg, pack);
        assertEquals(pack, deck.getCards());
        assertEquals(pack.getFirst(), deck.drawCard());
    }

    @Test
    void injectedOrderWithDuplicatesOrGapsIsRejected() {
        var cfg = config(2, 24, false);
        var pack = new ArrayList<>(new DurakCardFactory().createPack(cfg));
        var duplicated = new ArrayList<>(pack);
        duplicated.set(0, duplicated.get(1));
        assertThrows(DeckException.class, () -> DurakDeck.ordered(cfg, duplicated));

        var missing = new ArrayList<>(pack);
        missing.removeLast();
        assertThrows(DeckException.class, () -> DurakDeck.ordered(cfg, missing));
    }

    @Test
    void injectedOrderEndingInAJokerIsRejected() {
        var cfg = config(2, 54, true);
        var pack = new ArrayList<>(new DurakCardFactory().createPack(cfg));
        pack.remove(DurakCard.redJoker());
        pack.add(DurakCard.redJoker());
        assertThrows(DeckException.class, () -> DurakDeck.ordered(cfg, pack));
    }

    @Test
    void invalidSuitValueCombinationsAreRejected() {
        assertThrows(DurakRuleException.class, () -> new DurakCard(PokerCardSuit.HEARTS, PokerCardValue.JOKER));
        assertThrows(DurakRuleException.class, () -> new DurakCard(PokerCardSuit.RED_JOKER, PokerCardValue.ACE));
        assertDoesNotThrow(() -> new DurakCard(PokerCardSuit.HEARTS, PokerCardValue.ACE));
        assertDoesNotThrow(DurakCard::blackJoker);
    }

    @Test
    void cardCodesRoundTrip() {
        for (var card : new DurakCardFactory().createPack(config(2, 54, true))) {
            assertEquals(card, DurakCardFactory.fromCode(card.code()));
        }
        assertEquals("JR", DurakCard.redJoker().code());
        assertEquals("JB", DurakCard.blackJoker().code());
    }

    @Test
    void malformedCodesAreRejected() {
        for (var bad : List.of("", " ", "h2", "X5", "H", "H1", "H15", "H2X", "JOKER")) {
            assertThrows(DurakRuleException.class, () -> DurakCardFactory.fromCode(bad), bad);
        }
        assertThrows(DurakRuleException.class, () -> DurakCardFactory.fromCode(null));
    }

    @Test
    void existingPokerDeckFactoriesStayFreeOfJokers() {
        var factory = new DurakCardFactory();
        assertEquals(52, factory.create52CardDeck().size());
        assertEquals(36, factory.create36CardDeck().size());
        assertEquals(24, factory.create24CardDeck().size());
        assertTrue(factory.getCards().stream().noneMatch(DurakCard::isJoker));
        assertEquals(4, PokerCardSuit.suits().length);
    }
}
