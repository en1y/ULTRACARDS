package com.ultracards.games.briskula;

import com.ultracards.cards.italian.ItalianCardValue;

public class ItalianCardValueToBriskulaPointsConverter {
    public static int convertToBriskulaPoints(ItalianCardValue value) {
        return switch (value) {
            case ACE -> 11;
            case THREE -> 10;
            case JACK -> 2;
            case KNIGHT -> 3;
            case KING -> 4;
            default -> 0;
        };
    }
}
