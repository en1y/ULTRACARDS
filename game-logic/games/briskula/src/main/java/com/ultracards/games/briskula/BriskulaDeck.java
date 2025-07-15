package com.ultracards.games.briskula;

import com.ultracards.cards.ItalianCardType;
import com.ultracards.cards.ItalianCardValue;
import com.ultracards.templates.game.model.AbstractDeck;

import java.util.ArrayList;
import java.util.List;

public class BriskulaDeck extends AbstractDeck<ItalianCardType, ItalianCardValue, BriskulaCard, BriskulaHand> {

    private final int handCapacity;

    public BriskulaDeck(int handCapacity) {
        super(40);
        this.handCapacity = handCapacity;
    }

    @Override
    public List<BriskulaCard> createCards(int size) {
        return new BriskulaCardFactory().getCards();
    }

    @Override
    public BriskulaHand createHand(int cardsNum) {
        var hand = new BriskulaHand(handCapacity);
        hand.addCards(drawXCards(cardsNum));
        return hand;
    }

    public void appendCard (BriskulaCard card) {
        getCards().add(card);
    }

    public int getHandCapacity() {
        return handCapacity;
    }
}
