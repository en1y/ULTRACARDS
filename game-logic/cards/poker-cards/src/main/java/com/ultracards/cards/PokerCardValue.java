package com.ultracards.cards;

import com.ultracards.templates.cards.CardValueInterface;

import java.util.Locale;
import java.util.Objects;
import java.util.ResourceBundle;

public enum PokerCardValue implements CardValueInterface {
    TWO ("Two", 2),
    THREE ("Three", 3),
    FOUR ("Four", 4),
    FIVE ("Five", 5),
    SIX ("Six", 6),
    SEVEN ("Seven", 7),
    EIGHT ("Eight", 8),
    NINE ("Nine", 9),
    TEN ("Ten", 10),
    JACK ("Jack", 11),
    Queen("Queen", 12),
    KING ("King", 13),
    ACE ("Ace", 14);

    private static boolean useResourceBundle = false;
    private static ResourceBundle resourceBundle;

    private final String name;
    private final int number;

    PokerCardValue(String name, int number) {
        this.name = name;
        this.number = number;
    }

    public static void setLocale(Locale locale) {
        Objects.requireNonNull(locale);
        useResourceBundle = true;

        resourceBundle = ResourceBundle.getBundle("CardValueBundle", locale);
    }

    @Override
    public String getName() {
        if (useResourceBundle && resourceBundle.containsKey(name)) {
            return resourceBundle.getString(name);
        }
        return name;
    }

    @Override
    public String getSymbol() {
        var number = getNumber();
        return number > 10 || number == 1 ? name.substring(0, 1) : String.valueOf(number);
    }

    @Override
    public int getNumber() {
        return number;
    }
}
