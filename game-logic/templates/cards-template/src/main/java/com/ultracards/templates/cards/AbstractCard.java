package com.ultracards.templates.cards;

public abstract class AbstractCard<Suit extends CardSuitInterface, Value extends CardValueInterface, Card extends AbstractCard<Suit, Value, Card>> implements Comparable<Card> {

    private Suit suit;
    private Value value;

    public AbstractCard(Suit suit, Value value) {
        this.suit = suit;
        this.value = value;
    }

    public Suit getSuit() {
        return suit;
    }

    public Value getValue() {
        return value;
    }

    public void setValue(Value value) {
        this.value = value;
    }

    public void setSuit(Suit suit) {
        this.suit = suit;
    }

    @Override
    public int compareTo(Card o) {
        return Integer.compare(getValue().getNumber(), o.getValue().getNumber());
    }
}
