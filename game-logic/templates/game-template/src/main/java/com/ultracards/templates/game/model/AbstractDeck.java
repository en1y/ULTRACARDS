package com.ultracards.templates.game.model;

import com.ultracards.templates.cards.AbstractCard;
import com.ultracards.templates.cards.CardTypeInterface;
import com.ultracards.templates.cards.CardValueInterface;
import com.ultracards.templates.game.interfaces.DeckInterface;

import java.util.List;
import java.util.StringJoiner;

public abstract class AbstractDeck
        <CardType extends CardTypeInterface,
                CardValue extends CardValueInterface,
                Card extends AbstractCard<CardType, CardValue, Card>,
                Hand extends AbstractHand<CardType, CardValue, Card>>
        implements DeckInterface
            <CardType, CardValue, Card, Hand, AbstractDeck<CardType, CardValue, Card, Hand>> {

    private List<Card> cards;
    private int size;

    public AbstractDeck(int size) {
        init(size);
    }

    @Override
    public int getSize() {
        return size;
    }

    @Override
    public List<Card> getCards() {
        return cards;
    }

    @Override
    public void setSize(int size) {
        this.size = size;
    }

    @Override
    public void setCards(List<Card> cards) {
        this.cards = cards;
    }

    @Override
    public String toString() {
        var res = new StringJoiner(", ");

        for (var card : getCards()) {
            res.add(card.toString());
        }

        return "Deck: [" + res + "]";
    }
}
