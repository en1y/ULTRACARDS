package com.ultracards.cards;

import com.ultracards.templates.cards.AbstractCard;

import java.util.Locale;

public class ItalianCard extends AbstractCard<ItalianCardType, ItalianCardValue> {

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
