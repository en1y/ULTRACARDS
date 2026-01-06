package com.ultracards.cards;

import com.ultracards.templates.cards.AbstractCard;

import java.util.Locale;

public class PokerCard<Card extends PokerCard<Card>> extends AbstractCard<PokerCardSuit, PokerCardValue, Card> {

    public PokerCard(PokerCardSuit pokerCardSuit, PokerCardValue pokerCardValue) {
        super(pokerCardSuit, pokerCardValue);
    }

    public static void setLocale (Locale locale) {
        PokerCardSuit.setLocale(locale);
        PokerCardValue.setLocale(locale);
    }

    @Override
    public String toString() {
        return getValue().getName() + " " + getSuit().getSuitName();
    }
}
