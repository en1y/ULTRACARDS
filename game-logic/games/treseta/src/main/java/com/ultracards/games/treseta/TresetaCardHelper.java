package com.ultracards.games.treseta;

import com.ultracards.cards.ItalianCardSuit;
import com.ultracards.cards.ItalianCardValue;

import java.util.List;

public class TresetaCardHelper {
    public static int calculatePoints (ItalianCardValue value) {
        return switch (value) {
            case ACE -> 3;
            case TWO, THREE, KING, KNIGHT, JACK -> 1;
            default -> 0;
        };
    }

    public static int compareTwoCards(ItalianCardSuit roundTrump, TresetaCard card1, TresetaCard card2) {
        var isThisTrump = card1.getSuit().equals(roundTrump);
        var isOtherTrump = card2.getSuit().equals(roundTrump);

        if (isThisTrump && !isOtherTrump) return 1;
        if (!isThisTrump && isOtherTrump) return -1;

        return compareByValue(card1, card2);
    }

    private static final List<ItalianCardValue> cardsByStrength = List.of(
            ItalianCardValue.THREE, ItalianCardValue.TWO, ItalianCardValue.ACE,
            ItalianCardValue.KING, ItalianCardValue.KNIGHT, ItalianCardValue.JACK,
            ItalianCardValue.SEVEN, ItalianCardValue.SIX, ItalianCardValue.FIVE, ItalianCardValue.FOUR);

    public static int compareByValue(TresetaCard card1, TresetaCard card2) {
        var c1val = card1.getValue();
        var c2val = card2.getValue();
        if (c1val.equals(c2val)) return 0;
        for (var val: cardsByStrength) {
            if (val.equals(c1val)) return 1;
            if (val.equals(c2val)) return -1;
        }
        throw new RuntimeException("Such card value does not exist!?");
    }
}
