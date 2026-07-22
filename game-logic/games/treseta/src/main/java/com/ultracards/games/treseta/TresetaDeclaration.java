package com.ultracards.games.treseta;

import com.ultracards.cards.ItalianCardSuit;
import com.ultracards.cards.ItalianCardValue;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * A Treseta declaration: three or four of a kind (aces, twos or threes) or a
 * Napolitana (ace, two and three of one suit). Points are multiplied by three
 * to avoid floating point, matching TresetaCard.
 */
public record TresetaDeclaration(Type type, Set<ItalianCardSuit> suits) {

    public enum Type {
        ACES(ItalianCardValue.ACE),
        TWOS(ItalianCardValue.TWO),
        THREES(ItalianCardValue.THREE),
        NAPOLITANA(null);

        private final ItalianCardValue value;

        Type(ItalianCardValue value) {
            this.value = value;
        }

        public ItalianCardValue getValue() {
            return value;
        }
    }

    public TresetaDeclaration {
        suits = Set.copyOf(suits);
        if (type == Type.NAPOLITANA) {
            if (suits.size() != 1)
                throw new IllegalArgumentException("A Napolitana is declared in exactly one suit.");
        } else if (suits.size() < 3) {
            throw new IllegalArgumentException("Three or four cards of the same value are needed for a declaration.");
        }
    }

    public int getPoints() {
        if (type == Type.NAPOLITANA) return 9;
        return suits.size() == 4 ? 12 : 9;
    }

    /**
     * The cards a player must hold to make this declaration.
     */
    public List<TresetaCard> getCards() {
        var cards = new ArrayList<TresetaCard>();
        if (type == Type.NAPOLITANA) {
            var suit = suits.iterator().next();
            cards.add(new TresetaCard(suit, ItalianCardValue.ACE));
            cards.add(new TresetaCard(suit, ItalianCardValue.TWO));
            cards.add(new TresetaCard(suit, ItalianCardValue.THREE));
            return cards;
        }
        for (var suit : suits) cards.add(new TresetaCard(suit, type.getValue()));
        return cards;
    }

    /**
     * Infers the declaration formed by the selected cards, or throws if they form none.
     */
    public static TresetaDeclaration fromCards(List<TresetaCard> cards) {
        if (cards == null || cards.size() < 3 || cards.size() > 4)
            throw new IllegalArgumentException("Select three or four cards to declare.");
        var suits = EnumSet.noneOf(ItalianCardSuit.class);
        var values = EnumSet.noneOf(ItalianCardValue.class);
        for (var card : cards) {
            suits.add(card.getSuit());
            values.add(card.getValue());
        }
        if (values.size() == 1 && suits.size() == cards.size())
            for (var type : Type.values())
                if (type.getValue() != null && values.contains(type.getValue()))
                    return new TresetaDeclaration(type, suits);
        if (suits.size() == 1 && cards.size() == 3
                && values.equals(EnumSet.of(ItalianCardValue.ACE, ItalianCardValue.TWO, ItalianCardValue.THREE)))
            return new TresetaDeclaration(Type.NAPOLITANA, suits);
        throw new IllegalArgumentException("Selected cards do not form a valid declaration.");
    }
}
