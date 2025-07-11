package com.ultracards.cards;

import com.ultracards.templates.cards.AbstractCard;

import java.util.Locale;

public class PokerCard extends AbstractCard<PokerCardType, PokerCardValue> {

    public PokerCard(PokerCardType pokerCardType, PokerCardValue pokerCardValue) {
        super(pokerCardType, pokerCardValue);
    }

    public static void setLocale (Locale locale) {
        PokerCardType.setLocale(locale);
        PokerCardValue.setLocale(locale);
    }

    @Override
    public String toString() {
        return getValue().getName() + " " + getType().getName();
    }
}
