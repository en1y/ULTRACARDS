package com.ultracards.games.treseta;

import com.ultracards.cards.italian.ItalianCard;
import com.ultracards.cards.italian.ItalianCardSuit;
import com.ultracards.cards.italian.ItalianCardValue;

public class TresetaCard extends ItalianCard<TresetaCard> {
    private final int points; // points multiplied by three to avoid floating point
    public TresetaCard() {
        super(ItalianCardSuit.BASTONI, ItalianCardValue.KING);
        this.points = TresetaCardHelper.calculatePoints(ItalianCardValue.KING);
    }
    public TresetaCard(ItalianCardSuit italianCardSuit, ItalianCardValue italianCardValue) {
        super(italianCardSuit, italianCardValue);
        this.points = TresetaCardHelper.calculatePoints(italianCardValue);
    }

    public int getPoints() {
        return points;
    }

    public int compareTo (ItalianCardSuit roundTrump, TresetaCard other) {
        return TresetaCardHelper.compareTwoCards(roundTrump, this, other);
    }

    @Override
    public int compareTo(TresetaCard o) {
        return TresetaCardHelper.compareByValue(this, o);
    }
}
