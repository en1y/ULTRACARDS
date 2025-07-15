package com.ultracards.cards;

import com.ultracards.templates.cards.AbstractCard;

import java.util.Locale;

public class ItalianCard<Card extends ItalianCard<Card>> extends AbstractCard<ItalianCardType, ItalianCardValue, Card> {

    public ItalianCard(ItalianCardType italianCardType, ItalianCardValue italianCardValue) {
        super(italianCardType, italianCardValue);
    }

    public static void setLocale (Locale locale) {
        ItalianCardType.setLocale(locale);
        ItalianCardValue.setLocale(locale);
    }

    @Override
    public String toString() {
        return getValue().getName() + " " + getType().getName();
    }
}
