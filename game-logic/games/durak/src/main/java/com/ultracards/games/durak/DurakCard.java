package com.ultracards.games.durak;

import com.ultracards.cards.PokerCard;
import com.ultracards.cards.PokerCardSuit;
import com.ultracards.cards.PokerCardValue;

import java.util.Objects;

/**
 * A Durak card: either a regular suited poker card, or one of the two Jokers.
 * Invalid combinations such as {@code HEARTS + JOKER} or {@code RED_JOKER + ACE} are rejected here.
 */
public class DurakCard extends PokerCard<DurakCard> {

    public DurakCard(PokerCardSuit suit, PokerCardValue value) {
        super(validate(suit, value), value);
    }

    private static PokerCardSuit validate(PokerCardSuit suit, PokerCardValue value) {
        Objects.requireNonNull(suit, "suit");
        Objects.requireNonNull(value, "value");
        if (suit.isJokerSuit() != (value == PokerCardValue.JOKER)) {
            throw new DurakRuleException(DurakErrorCode.DURAK_JOKER_DISABLED,
                    "Invalid Durak card combination: %s + %s", suit, value);
        }
        return suit;
    }

    public static DurakCard redJoker() {
        return new DurakCard(PokerCardSuit.RED_JOKER, PokerCardValue.JOKER);
    }

    public static DurakCard blackJoker() {
        return new DurakCard(PokerCardSuit.BLACK_JOKER, PokerCardValue.JOKER);
    }

    public boolean isJoker() {
        return getSuit().isJokerSuit();
    }

    public boolean isRed() {
        return getSuit().isRed();
    }

    public boolean isBlack() {
        return getSuit().isBlack();
    }

    public int rank() {
        return getValue().getNumber();
    }

    /** Stable, locale-independent wire code: {@code H2..H14}, {@code JR}, {@code JB}. */
    public String code() {
        return switch (getSuit()) {
            case RED_JOKER -> "JR";
            case BLACK_JOKER -> "JB";
            default -> getSuit().name().charAt(0) + String.valueOf(rank());
        };
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DurakCard other)) return false;
        return getSuit() == other.getSuit() && getValue() == other.getValue();
    }

    @Override
    public int hashCode() {
        return Objects.hash(getSuit(), getValue());
    }

    @Override
    public String toString() {
        return code();
    }
}
