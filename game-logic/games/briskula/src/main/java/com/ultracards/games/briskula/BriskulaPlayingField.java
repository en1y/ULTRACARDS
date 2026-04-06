package com.ultracards.games.briskula;

import com.ultracards.cards.ItalianCardSuit;
import com.ultracards.cards.ItalianCardValue;
import com.ultracards.templates.game.model.AbstractPlayingField;

import java.util.List;

public class BriskulaPlayingField extends AbstractPlayingField<ItalianCardSuit, ItalianCardValue, BriskulaCard, BriskulaHand, BriskulaDeck, BriskulaPlayer> {

    private final ItalianCardSuit gameTrumpCardType;
    private final BriskulaGameConfig gameConfig;
    private int playedCards = 0;
    public BriskulaPlayingField(List<BriskulaPlayer> players, BriskulaGame game, ItalianCardSuit gameTrumpCardType, BriskulaGameConfig gameConfig) {
        super(players, game);
        this.gameTrumpCardType = gameTrumpCardType;
        this.gameConfig = gameConfig;
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

    @Override
    public boolean isTurnPlayed() {
        if (gameConfig.equals(BriskulaGameConfig.TWO_PLAYERS_FOUR_CARDS_IN_HAND_EACH)) {
            return playedCards >= 4;
        }
        return super.isTurnPlayed();
    }

    @Override
    public BriskulaPlayer getCurrentPlayer() {
        if (gameConfig.equals(BriskulaGameConfig.TWO_PLAYERS_FOUR_CARDS_IN_HAND_EACH)) {
            return getPlayers().get(playedCards % 2);
        }
        return super.getCurrentPlayer();
    }

    @Override
    public BriskulaPlayer getPlayerByPlayedCard(BriskulaCard card) {
        if (gameConfig.equals(BriskulaGameConfig.TWO_PLAYERS_FOUR_CARDS_IN_HAND_EACH)) {
            var index = getPlayedCards().indexOf(card);
            if (index == -1) {
                throw new IllegalArgumentException("Card " + card + " was not played in this turn.");
            }
            return getPlayers().get(index % 2);
        }
        return super.getPlayerByPlayedCard(card);
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
        playedCards++;
        super.play(card, player);
    }
}
