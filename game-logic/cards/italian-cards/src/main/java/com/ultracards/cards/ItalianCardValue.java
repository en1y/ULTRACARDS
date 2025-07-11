package com.ultracards.cards;

import com.ultracards.templates.cards.CardValueInterface;

import java.util.Locale;
import java.util.Objects;
import java.util.ResourceBundle;

public enum ItalianCardValue implements CardValueInterface {
    ACE ("Ace", 1),
    TWO ("Two", 2),
    THREE ("Three", 3),
    FOUR ("Four", 4),
    FIVE ("Five", 5),
    SIX ("Six", 6),
    SEVEN ("Seven", 7),
    JACK ("Jack", 11),
    KNIGHT ("Knight", 12),
    KING ("King", 13);

    private static boolean useResourceBundle = false;
    private static ResourceBundle resourceBundle;

    /**
     * Use this method if you want to use the resource bundle for card number names and values.
     * @param locale - with this locale the resource bundle will be loaded
     */
    static void setLocale(Locale locale) {
        Objects.requireNonNull(locale);
        useResourceBundle = true;

        resourceBundle = ResourceBundle.getBundle("CardValueBundle", locale);
    }

    private final String name;
    private final int number;

    ItalianCardValue(String name, int number) {
        this.name = name;
        this.number = number;
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
        return String.valueOf(getNumber());
    }

    @Override
    public int getNumber() {
        return number;
    }
}
