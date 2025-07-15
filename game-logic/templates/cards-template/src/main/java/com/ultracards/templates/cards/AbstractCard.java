package com.ultracards.templates.cards;

public abstract class AbstractCard<Type extends CardTypeInterface, Value extends CardValueInterface, Card extends AbstractCard<Type, Value, Card>> implements Comparable<Card> {

    private final Type type;
    private final Value value;

    public AbstractCard(Type type, Value value) {
        this.type = type;
        this.value = value;
    }

    public Type getType() {
        return type;
    }

    public Value getValue() {
        return value;
    }

    @Override
    public int compareTo(Card o) {
        return Integer.compare(getValue().getNumber(), o.getValue().getNumber());
    }
}
