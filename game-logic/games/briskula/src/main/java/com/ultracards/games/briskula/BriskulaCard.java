package com.ultracards.games.briskula;

import com.ultracards.cards.ItalianCard;
import com.ultracards.cards.ItalianCardType;
import com.ultracards.cards.ItalianCardValue;


public class BriskulaCard extends ItalianCard {

    private final int points;

    public BriskulaCard(ItalianCardType italianCardType, ItalianCardValue italianCardValue) {
        super(italianCardType, italianCardValue);
        this.points =
                ItalianCardValueToBriskulaPointsConverter.convertToBriskulaPoints(italianCardValue);
    }

    public int getPoints() {
        return points;
    }
}
