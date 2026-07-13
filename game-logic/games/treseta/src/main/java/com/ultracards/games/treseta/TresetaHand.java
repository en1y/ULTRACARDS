package com.ultracards.games.treseta;

import com.ultracards.cards.ItalianCardSuit;
import com.ultracards.cards.ItalianCardValue;
import com.ultracards.templates.game.model.AbstractHand;

public class TresetaHand extends AbstractHand<ItalianCardSuit, ItalianCardValue, TresetaCard> {
    public TresetaHand(int capacity) {
        super(capacity);
    }

    public boolean containsSuit(ItalianCardSuit suit) {
        for (var c: getCards())
            if (c.getSuit() == suit) return true;
        return false;
    }
}
