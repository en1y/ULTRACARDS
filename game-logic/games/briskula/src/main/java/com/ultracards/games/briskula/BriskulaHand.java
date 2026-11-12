package com.ultracards.games.briskula;

import com.ultracards.cards.italian.ItalianCardSuit;
import com.ultracards.cards.italian.ItalianCardValue;
import com.ultracards.templates.game.model.AbstractHand;

public class BriskulaHand extends AbstractHand<ItalianCardSuit, ItalianCardValue, BriskulaCard> {
    public BriskulaHand(int capacity) {
        super(capacity);
    }
}
