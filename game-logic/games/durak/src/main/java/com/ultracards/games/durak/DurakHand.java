package com.ultracards.games.durak;

import com.ultracards.cards.poker.PokerCardSuit;
import com.ultracards.cards.poker.PokerCardValue;
import com.ultracards.templates.game.model.AbstractHand;

public class DurakHand extends AbstractHand<PokerCardSuit, PokerCardValue, DurakCard> {
    // A defender who takes can hold far more than six cards, so the capacity is the whole pack.
    public DurakHand(int capacity) {
        super(capacity);
    }

    public int size() {
        return getCardsNum();
    }
}
