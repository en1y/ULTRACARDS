package com.ultracards.templates.cards;

public abstract class AbstractCard<Type extends CardTypeInterface, Value extends CardValueInterface> implements Comparable<AbstractCard<Type, Value>> {

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
    public int compareTo(AbstractCard<Type, Value> o) {
        return Integer.compare(getValue().getNumber(), o.getValue().getNumber());
    }
}
