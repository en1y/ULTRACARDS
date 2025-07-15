package com.ultracards.templates.game.model;

import com.ultracards.templates.cards.AbstractCard;
import com.ultracards.templates.cards.CardTypeInterface;
import com.ultracards.templates.cards.CardValueInterface;
import com.ultracards.templates.game.interfaces.HandInterface;

import java.util.List;
import java.util.StringJoiner;

public abstract class AbstractHand
        <CardType extends CardTypeInterface,
                CardValue extends CardValueInterface,
                Card extends AbstractCard<CardType, CardValue, Card>>
        implements HandInterface<CardType, CardValue, Card> {

    private List<Card> cards;
    private int capacity;
    private int cardsNum;

    public AbstractHand(int capacity) {
        init(capacity);
    }

    @Override
    public List<Card> getCards() {
        return cards;
    }

    @Override
    public void setCards(List<Card> cards) {
        this.cards = cards;
    }

    @Override
    public int getCapacity() {
        return capacity;
    }

    @Override
    public void setCapacity(int capacity) {
        this.capacity = capacity;
    }

    @Override
    public int getCardsNum() {
        return cardsNum;
    }

    @Override
    public void setCardsNum(int cardsNum) {
        this.cardsNum = cardsNum;
    }

    @Override
    public String toString() {
        var res = new StringJoiner(", ");
        for (var card : cards) {
            res.add(card.toString());
        }
        return "Hand: [" + res + "]";
    }
}
