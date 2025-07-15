package com.ultracards.games.briskula;

import com.ultracards.cards.ItalianCardType;
import com.ultracards.cards.ItalianCardValue;
import com.ultracards.templates.game.model.AbstractPlayingField;

public class BriskulaPlayingField extends AbstractPlayingField<ItalianCardType, ItalianCardValue, BriskulaCard, BriskulaHand, BriskulaDeck, BriskulaPlayer> {

    private final ItalianCardType gameTrumpCardType;

    public BriskulaPlayingField(ItalianCardType gameTrumpCardType) {
        super();
        this.gameTrumpCardType = gameTrumpCardType;
    }

    @Override
    public BriskulaPlayer determineRoundWinner() {
        var playedCards = getPlayedCards();
        var winningCard = playedCards.stream().max(
                (bc1, bc2) -> bc1.compareTo(gameTrumpCardType, playedCards.get(0).getType(), bc2)
        ).orElseThrow(
            () -> new IllegalStateException("Could not determine round winner, there were probably no cards played")
        );
        return getPlayerByPlayedCard(winningCard);
    }

    public int calcTotalPoints() {
        var res = 0;
        for (var card : getPlayedCards()) {
            res += card.getPoints();
        }
        return res;
    }
}
