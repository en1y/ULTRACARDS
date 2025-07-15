package com.ultracards.games.briskula;

import com.ultracards.cards.ItalianCardType;
import com.ultracards.cards.ItalianCardValue;
import com.ultracards.templates.game.model.AbstractPlayingField;

public class BriskulaPlayingField extends AbstractPlayingField<ItalianCardType, ItalianCardValue, BriskulaCard, BriskulaHand, BriskulaDeck, BriskulaPlayer> {

    @Override
    public BriskulaPlayer determineRoundWinner() {
        throw new UnsupportedOperationException("BriskulaPlayingField.determineRoundWinner() not supported yet."); // TODO: implement this method
    }
}
