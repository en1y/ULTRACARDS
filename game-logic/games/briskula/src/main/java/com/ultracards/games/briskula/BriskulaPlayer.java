package com.ultracards.games.briskula;

import com.ultracards.cards.ItalianCardType;
import com.ultracards.cards.ItalianCardValue;
import com.ultracards.templates.game.model.AbstractPlayer;

public class BriskulaPlayer extends AbstractPlayer<ItalianCardType, ItalianCardValue, BriskulaCard, BriskulaHand, BriskulaDeck> {
    public BriskulaPlayer(String name) {
        super(name);
    }
}
