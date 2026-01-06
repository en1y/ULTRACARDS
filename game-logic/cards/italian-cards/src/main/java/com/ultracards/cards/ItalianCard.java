package com.ultracards.cards;

import com.ultracards.templates.cards.AbstractCard;

import java.util.Locale;

public class ItalianCard<Card extends ItalianCard<Card>> extends AbstractCard<ItalianCardSuit, ItalianCardValue, Card> {

    public ItalianCard(ItalianCardSuit italianCardSuit, ItalianCardValue italianCardValue) {
        super(italianCardSuit, italianCardValue);
    }

    public static void setLocale (Locale locale) {
        ItalianCardSuit.setLocale(locale);
        ItalianCardValue.setLocale(locale);
    }

    @Override
    public String toString() {
        return getValue().getName() + " " + getSuit().getSuitName();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof ItalianCard<?> card) {
            return getSuit().equals(card.getSuit()) && getValue().equals(card.getValue());
        }
        return false;
    }
}
