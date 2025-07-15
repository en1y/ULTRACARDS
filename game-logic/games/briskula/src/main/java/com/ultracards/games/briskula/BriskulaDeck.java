package com.ultracards.games.briskula;

import com.ultracards.cards.ItalianCardType;
import com.ultracards.cards.ItalianCardValue;
import com.ultracards.templates.game.model.AbstractDeck;

import java.util.List;

public class BriskulaDeck extends AbstractDeck<ItalianCardType, ItalianCardValue, BriskulaCard, BriskulaHand> {

    private final int handCapacity;

    public BriskulaDeck(int handCapacity) {
        super(40);
        this.handCapacity = handCapacity;
        setCards(createCards(getSize()));
    }

    @Override
    public List<BriskulaCard> createCards(int size) {
        throw new UnsupportedOperationException("BriskulaDeck.createCards() not supported yet."); // TODO: implement this method
//        return new ItalianCardFactory().getCards();
    }

    @Override
    public BriskulaHand createHand(int cardsNum) {
        throw new UnsupportedOperationException("BriskulaDeck.createHand() not supported yet."); // TODO: implement this method
    }

    public int getHandCapacity() {
        return handCapacity;
    }
}
