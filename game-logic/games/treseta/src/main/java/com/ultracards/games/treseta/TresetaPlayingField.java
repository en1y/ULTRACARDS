package com.ultracards.games.treseta;

import com.ultracards.cards.ItalianCardSuit;
import com.ultracards.cards.ItalianCardValue;
import com.ultracards.templates.game.model.AbstractGame;
import com.ultracards.templates.game.model.AbstractPlayingField;

import java.util.List;

public class TresetaPlayingField extends AbstractPlayingField<ItalianCardSuit, ItalianCardValue, TresetaCard, TresetaHand, TresetaDeck, TresetaPlayer> {

    private ItalianCardSuit roundTrumpSuit = null;
    private final int roundNum;
    public TresetaPlayingField(List<TresetaPlayer> tresetaPlayers, AbstractGame<ItalianCardSuit, ItalianCardValue, TresetaCard, TresetaHand, TresetaDeck, TresetaPlayer, ?> game, int roundNum) {
        super(tresetaPlayers, game);
        this.roundNum = roundNum;
    }

    @Override
    public void play(TresetaCard card, TresetaPlayer player) {
        if (roundTrumpSuit == null) {
            roundTrumpSuit = card.getSuit();
        } else if (!card.getSuit().equals(roundTrumpSuit) && player.getHand().containsSuit(roundTrumpSuit)) {
            throw new IllegalArgumentException("Player must play a card of the same suit as the first played card if they have that suit");
        }
        super.play(card, player);
    }

    @Override
    public TresetaPlayer determineRoundWinner() {
        var trumpSuit = getPlayedCards().getFirst().getSuit();
        var winningCard = getPlayedCards().stream().max(
                (tc1, tc2) -> tc1.compareTo(trumpSuit, tc2)
        ).orElseThrow(
                () -> new IllegalStateException("Could not determine round winner, there were probably no cards played")
        );
        return getPlayerByPlayedCard(winningCard);
    }

    public int calcTotalPoints() {
        var cards = getPlayedCards();
        var res = 0;
        for (var c: cards) res += c.getPoints();
        return res + (roundNum == 0 ? 3: 0);
    }
}
