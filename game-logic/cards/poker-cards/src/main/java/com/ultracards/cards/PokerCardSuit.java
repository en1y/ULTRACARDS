package com.ultracards.cards;

import com.ultracards.templates.cards.CardSuitInterface;

import java.util.Locale;
import java.util.Objects;
import java.util.ResourceBundle;

public enum PokerCardSuit implements CardSuitInterface {
    HEARTS ("Hearts"),
    DIAMONDS ("Diamonds"),
    CLUBS ("Clubs"),
    SPADES ("Spades"),
    /**
     * Pseudo-suits for the two Jokers. Only used by games that explicitly enable Jokers
     * (currently Durak). Code iterating real suits must use {@link #suits()}.
     */
    RED_JOKER ("Red Joker"),
    BLACK_JOKER ("Black Joker");

    private static final PokerCardSuit[] SUITS = {HEARTS, DIAMONDS, CLUBS, SPADES};

    /** The four real suits, excluding the Joker pseudo-suits. */
    public static PokerCardSuit[] suits() {
        return SUITS.clone();
    }

    public boolean isJokerSuit() {
        return this == RED_JOKER || this == BLACK_JOKER;
    }

    /** Hearts and diamonds are red; the red Joker counts as red. */
    public boolean isRed() {
        return this == HEARTS || this == DIAMONDS || this == RED_JOKER;
    }

    public boolean isBlack() {
        return this == CLUBS || this == SPADES || this == BLACK_JOKER;
    }

    private static boolean useResourceBundle = false;
    private static ResourceBundle resourceBundle;

    private final String name;

    PokerCardSuit(String name) {
        this.name = name;
    }

    /**
     * Use this method if you want to use the resource bundle for card type names.
     * @param locale - with this locale the resource bundle will be loaded
     */
    public static void setLocale(Locale locale) {
        Objects.requireNonNull(locale);
        useResourceBundle = true;

        resourceBundle = ResourceBundle.getBundle("CardTypeBundle", locale);
    }

    /**
     * When the resource bundle is not used, this method returns the name of the card type.
     * When the resource bundle is used, it returns the name from the resource bundle.
     * If the resource bundle does not contain the name, it returns the name of the card type.
     */
    public String getSuitName() {
        if (useResourceBundle && resourceBundle.containsKey(name)) {
            return resourceBundle.getString(name);
        }
        return name;
    }
}
