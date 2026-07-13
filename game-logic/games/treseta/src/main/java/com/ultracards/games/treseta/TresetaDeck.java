package com.ultracards.games.treseta;

import com.ultracards.cards.ItalianCardSuit;
import com.ultracards.cards.ItalianCardValue;
import com.ultracards.templates.game.model.AbstractDeck;

import java.util.List;

public class TresetaDeck extends AbstractDeck<ItalianCardSuit, ItalianCardValue, TresetaCard, TresetaHand> {
    private final int handCapacity;

    public TresetaDeck(int size, int handCapacity) {
        super(size);
        this.handCapacity = handCapacity;
    }

    @Override
    public List<TresetaCard> createCards(int size) {
        return new TresetaCardFactory().getCards();
    }

    @Override
    public TresetaHand createHand(int cardsNum) {
        var hand = new TresetaHand(getHandCapacity());
        hand.addCards(drawXCards(cardsNum));
        return hand;
    }

    public int getHandCapacity() {
        return handCapacity;
    }
}
