package com.ultracards.games.durak;

import com.ultracards.cards.PokerCardSuit;
import com.ultracards.cards.PokerCardValue;
import com.ultracards.templates.game.model.AbstractPlayer;

public class DurakPlayer extends AbstractPlayer<PokerCardSuit, PokerCardValue, DurakCard, DurakHand, DurakDeck> {

    private final int seat;

    public DurakPlayer(String name, int seat) {
        super(name);
        this.seat = seat;
    }

    public int getSeat() {
        return seat;
    }

    public int handSize() {
        return getHand() == null ? 0 : getHand().getCardsNum();
    }

    public boolean hasCards() {
        return handSize() > 0;
    }

    @Override
    public String toString() {
        return "DurakPlayer[" + seat + ":" + getName() + "]";
    }
}
