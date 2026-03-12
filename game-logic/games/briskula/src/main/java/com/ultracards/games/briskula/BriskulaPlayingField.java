package com.ultracards.games.briskula;

import com.ultracards.cards.ItalianCardSuit;
import com.ultracards.cards.ItalianCardValue;
import com.ultracards.templates.game.model.AbstractPlayingField;

import java.util.List;

public class BriskulaPlayingField extends AbstractPlayingField<ItalianCardSuit, ItalianCardValue, BriskulaCard, BriskulaHand, BriskulaDeck, BriskulaPlayer> {

    private final ItalianCardSuit gameTrumpCardType;

    public BriskulaPlayingField(List<BriskulaPlayer> players, BriskulaGame game, ItalianCardSuit gameTrumpCardType) {
        super(players, game);
        this.gameTrumpCardType = gameTrumpCardType;
    }

    @Override
    public BriskulaPlayer determineRoundWinner() {
        var playedCards = getPlayedCards();
        var winningCard = playedCards.stream().max(
                (bc1, bc2) -> bc1.compareTo(gameTrumpCardType, playedCards.getFirst().getSuit(), bc2)
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

    @Override
    public void play(BriskulaCard card, BriskulaPlayer player) {
        super.play(card, player);
    }
}
