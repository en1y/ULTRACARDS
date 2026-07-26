package com.ultracards.games.durak;

import com.ultracards.cards.PokerCardSuit;
import com.ultracards.cards.PokerCardValue;
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
