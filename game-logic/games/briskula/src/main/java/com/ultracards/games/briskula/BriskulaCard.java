package com.ultracards.games.briskula;

import com.ultracards.cards.ItalianCard;
import com.ultracards.cards.ItalianCardSuit;
import com.ultracards.cards.ItalianCardValue;


public class BriskulaCard extends ItalianCard<BriskulaCard> {

    private final int points;

    public BriskulaCard() {
        this(ItalianCardSuit.BASTONI, ItalianCardValue.KING); // Default constructor for serialization/deserialization
    }

    public BriskulaCard(ItalianCardSuit italianCardSuit, ItalianCardValue italianCardValue) {
        super(italianCardSuit, italianCardValue);
        this.points =
                ItalianCardValueToBriskulaPointsConverter.convertToBriskulaPoints(italianCardValue);
    }

    public int getPoints() {
        return points;
    }

    public int compareTo(ItalianCardSuit gameTrumpCardType, ItalianCardSuit roundTrumpCardType, BriskulaCard card) {
        var isThisGameTrump = getSuit().equals(gameTrumpCardType);
        var isOtherGameTrump = card.getSuit().equals(gameTrumpCardType);

        if (isThisGameTrump && !isOtherGameTrump) {
            return 1; // this card is trump, other is not
        } else if (!isThisGameTrump && isOtherGameTrump) {
            return -1; // other card is trump, this is not
        }

        var isThisRoundTrump = getSuit().equals(roundTrumpCardType);
        var isOtherRoundTrump = card.getSuit().equals(roundTrumpCardType);

        if (isThisRoundTrump && !isOtherRoundTrump) {
            return 1; // this card is round trump, other is not
        } else if (!isThisRoundTrump && isOtherRoundTrump) {
            return -1; // other card is round trump, this is not
        }

        return this.compareTo(card);
    }

    @Override
    public int compareTo(BriskulaCard card) {
        var comparedPoints = Integer.compare(getPoints(), card.getPoints());
        return comparedPoints == 0 ? super.compareTo(card): comparedPoints;
    }
}
