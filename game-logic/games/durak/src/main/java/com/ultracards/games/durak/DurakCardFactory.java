package com.ultracards.games.durak;

import com.ultracards.cards.PokerCardFactoryInterface;
import com.ultracards.cards.PokerCardSuit;
import com.ultracards.cards.PokerCardValue;

import java.util.ArrayList;
import java.util.List;

public class DurakCardFactory implements PokerCardFactoryInterface<DurakCard> {

    @Override
    public List<DurakCard> create24CardDeck() {
        return suited(9);
    }

    @Override
    public List<DurakCard> create36CardDeck() {
        return suited(6);
    }

    @Override
    public List<DurakCard> create52CardDeck() {
        return suited(2);
    }

    /** The full pack for a configuration, in canonical (unshuffled) order. */
    public List<DurakCard> createPack(DurakGameConfig config) {
        var cards = suited(config.lowestRank());
        if (config.jokersEnabled()) {
            cards.add(DurakCard.redJoker());
            cards.add(DurakCard.blackJoker());
        }
        return cards;
    }

    private List<DurakCard> suited(int lowestRank) {
        var res = new ArrayList<DurakCard>();
        for (var suit : PokerCardSuit.suits()) {
            for (var value : PokerCardValue.values()) {
                if (value != PokerCardValue.JOKER && value.getNumber() >= lowestRank) {
                    res.add(new DurakCard(suit, value));
                }
            }
        }
        return res;
    }

    /** Parses a stable card code such as {@code H14}, {@code JR} or {@code JB}. */
    public static DurakCard fromCode(String code) {
        if (code == null || code.isBlank()) {
            throw new DurakRuleException(DurakErrorCode.DURAK_CARD_NOT_IN_HAND, "Blank card code.");
        }
        if (code.equals("JR")) return DurakCard.redJoker();
        if (code.equals("JB")) return DurakCard.blackJoker();
        var suit = switch (code.charAt(0)) {
            case 'H' -> PokerCardSuit.HEARTS;
            case 'D' -> PokerCardSuit.DIAMONDS;
            case 'C' -> PokerCardSuit.CLUBS;
            case 'S' -> PokerCardSuit.SPADES;
            default -> throw new DurakRuleException(DurakErrorCode.DURAK_CARD_NOT_IN_HAND,
                    "Unknown card code: %s", code);
        };
        int rank;
        try {
            rank = Integer.parseInt(code.substring(1));
        } catch (NumberFormatException e) {
            throw new DurakRuleException(DurakErrorCode.DURAK_CARD_NOT_IN_HAND, "Unknown card code: %s", code);
        }
        for (var value : PokerCardValue.values()) {
            if (value != PokerCardValue.JOKER && value.getNumber() == rank) {
                return new DurakCard(suit, value);
            }
        }
        throw new DurakRuleException(DurakErrorCode.DURAK_CARD_NOT_IN_HAND, "Unknown card code: %s", code);
    }
}
