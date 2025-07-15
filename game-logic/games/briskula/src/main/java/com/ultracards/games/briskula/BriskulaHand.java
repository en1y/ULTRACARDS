package com.ultracards.games.briskula;

import com.ultracards.cards.ItalianCardType;
import com.ultracards.cards.ItalianCardValue;
import com.ultracards.templates.game.model.AbstractHand;

public class BriskulaHand extends AbstractHand<ItalianCardType, ItalianCardValue, BriskulaCard> {
    public BriskulaHand(int capacity) {
        super(capacity);
    }
}
