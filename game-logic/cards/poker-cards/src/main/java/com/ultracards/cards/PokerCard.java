package com.ultracards.cards;

import com.ultracards.templates.cards.AbstractCard;

import java.util.Locale;

public class PokerCard<Card extends PokerCard<Card>> extends AbstractCard<PokerCardType, PokerCardValue, Card> {

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
