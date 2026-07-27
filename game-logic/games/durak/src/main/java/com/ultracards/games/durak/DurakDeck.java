package com.ultracards.games.durak;

import com.ultracards.cards.PokerCardSuit;
import com.ultracards.cards.PokerCardValue;
import com.ultracards.templates.game.exceptions.DeckException;
import com.ultracards.templates.game.model.AbstractDeck;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.random.RandomGenerator;

/**
 * The Durak stock. Its card order is fully determined by the caller so dealing, trump selection
 * and refills can be asserted deterministically; the last card of the order is the trump indicator.
 */
public class DurakDeck extends AbstractDeck<PokerCardSuit, PokerCardValue, DurakCard, DurakHand> {

    private final int handCapacity;

    private DurakDeck(List<DurakCard> orderedCards, int handCapacity) {
        super(0); // AbstractDeck#init runs before our fields exist; we install the real order below
        this.handCapacity = handCapacity;
        setCards(new ArrayList<>(orderedCards));
        setSize(orderedCards.size());
    }

    /**
     * Builds the deck order for a configuration: when the pack is bigger than the deal, one
     * randomly chosen <em>suited</em> card is reserved as the trump indicator and placed at the
     * bottom, guaranteeing a suited trump even if both Jokers end up in the remainder.
     */
    public static DurakDeck shuffled(DurakGameConfig config, RandomGenerator random) {
        var cards = new DurakCardFactory().createPack(config);
        var dealt = config.numberOfPlayers() * DurakGameConfig.CARDS_IN_HAND;
        if (cards.size() > dealt) {
            var suitedIndexes = new ArrayList<Integer>();
            for (int i = 0; i < cards.size(); i++) {
                if (!cards.get(i).isJoker()) suitedIndexes.add(i);
            }
            var indicator = cards.remove((int) suitedIndexes.get(random.nextInt(suitedIndexes.size())));
            shuffle(cards, random);
            cards.add(indicator);
        } else {
            shuffle(cards, random);
        }
        return ordered(config, cards);
    }

    /** Test/replay entry point: the caller supplies the exact order, last card being the indicator. */
    public static DurakDeck ordered(DurakGameConfig config, List<DurakCard> orderedCards) {
        var expected = new DurakCardFactory().createPack(config);
        if (orderedCards.size() != expected.size() || new HashSet<>(orderedCards).size() != expected.size()
                || !new HashSet<>(orderedCards).equals(new HashSet<>(expected))) {
            throw new DeckException("Durak deck order must contain exactly the %d cards of the configured pack",
                    expected.size());
        }
        if (orderedCards.getLast().isJoker()) {
            throw new DeckException("The trump indicator (last card) must be a suited card");
        }
        return new DurakDeck(orderedCards, expected.size());
    }

    private static void shuffle(List<DurakCard> cards, RandomGenerator random) {
        for (int i = cards.size() - 1; i > 0; i--) {
            var j = random.nextInt(i + 1);
            var tmp = cards.get(i);
            cards.set(i, cards.get(j));
            cards.set(j, tmp);
        }
    }

    @Override
    public List<DurakCard> createCards(int size) {
        return new ArrayList<>(); // real cards are installed by the constructor
    }

    @Override
    public DurakHand createHand(int cardsNum) {
        var hand = new DurakHand(handCapacity);
        hand.addCards(drawXCards(cardsNum));
        return hand;
    }

    /** An empty hand with room for a full pack, for players who are dealt card by card. */
    public DurakHand createEmptyHand() {
        return new DurakHand(handCapacity);
    }
}
