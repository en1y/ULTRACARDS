package com.ultracards.games.briskula;

import com.ultracards.cards.ItalianCard;
import com.ultracards.cards.ItalianCardType;
import com.ultracards.cards.ItalianCardValue;


public class BriskulaCard extends ItalianCard<BriskulaCard> {

    private final int points;

    public BriskulaCard(ItalianCardType italianCardType, ItalianCardValue italianCardValue) {
        super(italianCardType, italianCardValue);
        this.points =
                ItalianCardValueToBriskulaPointsConverter.convertToBriskulaPoints(italianCardValue);
    }

    public int getPoints() {
        return points;
    }

    public int compareTo(ItalianCardType gameTrumpCardType, ItalianCardType roundTrumpCardType, BriskulaCard card) {
        var isThisGameTrump = getType().equals(gameTrumpCardType);
        var isOtherGameTrump = getType().equals(gameTrumpCardType);

        if (isThisGameTrump && !isOtherGameTrump) {
            return 1; // this card is trump, other is not
        } else if (!isThisGameTrump && isOtherGameTrump) {
            return -1; // other card is trump, this is not
        }

        var isThisRoundTrump = getType().equals(roundTrumpCardType);
        var isOtherRoundTrump = getType().equals(roundTrumpCardType);

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
