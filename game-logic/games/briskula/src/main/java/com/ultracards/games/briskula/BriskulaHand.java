package com.ultracards.games.briskula;

import com.ultracards.cards.ItalianCardSuit;
import com.ultracards.cards.ItalianCardValue;
import com.ultracards.templates.game.model.AbstractHand;

public class BriskulaHand extends AbstractHand<ItalianCardSuit, ItalianCardValue, BriskulaCard> {
    public BriskulaHand(int capacity) {
        super(capacity);
    }
}
