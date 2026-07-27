package com.ultracards.gateway.dto.games.games;

import com.ultracards.cards.*;
import com.ultracards.templates.cards.AbstractCard;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A card on the wire, encoded with a stable, locale-independent code.
 *
 * <p>Poker: {@code H2..H14}, {@code D2..D14}, {@code C2..C14}, {@code S2..S14}, plus {@code JR}
 * and {@code JB} for the two Jokers. Italian: first letter of the suit enum name plus the value
 * number. Codes come from the enum names, never from translated suit names.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class GameCardDTO {
    private static final String RED_JOKER_CODE = "JR";
    private static final String BLACK_JOKER_CODE = "JB";

    private GameCardTypeDTO cardType;
    private String card;

    public static GameCardDTO createCardDTO(AbstractCard<?,?,?> card) {
        if (card instanceof PokerCard) {
            return new GameCardDTO(GameCardTypeDTO.POKER,
                    pokerCode((PokerCardSuit) card.getSuit(), card.getValue().getNumber()));
        } else if (card instanceof ItalianCard) {
            return new GameCardDTO(GameCardTypeDTO.ITALIAN,
                    card.getSuit().toString().charAt(0) + String.valueOf(card.getValue().getNumber()));
        }
        return null;
    }

    private static String pokerCode(PokerCardSuit suit, int number) {
        return switch (suit) {
            case RED_JOKER -> RED_JOKER_CODE;
            case BLACK_JOKER -> BLACK_JOKER_CODE;
            default -> suit.name().charAt(0) + String.valueOf(number);
        };
    }

    public AbstractCard<?,?,?> toCard() {
        if (cardType == null || card == null || card.isBlank()) {
            throw new IllegalArgumentException("Invalid card");
        }
        return switch (cardType) {
            case POKER -> toPokerCard();
            case ITALIAN -> toItalianCard();
        };
    }

    private PokerCard<?> toPokerCard() {
        if (RED_JOKER_CODE.equals(card)) {
            return new PokerCard<>(PokerCardSuit.RED_JOKER, PokerCardValue.JOKER);
        }
        if (BLACK_JOKER_CODE.equals(card)) {
            return new PokerCard<>(PokerCardSuit.BLACK_JOKER, PokerCardValue.JOKER);
        }
        var suit = switch (card.charAt(0)) {
            case 'H' -> PokerCardSuit.HEARTS;
            case 'D' -> PokerCardSuit.DIAMONDS;
            case 'C' -> PokerCardSuit.CLUBS;
            case 'S' -> PokerCardSuit.SPADES;
            default -> throw new IllegalArgumentException("Invalid card");
        };
        return new PokerCard<>(suit, pokerValue(parseNumber()));
    }

    private static PokerCardValue pokerValue(int number) {
        for (var v : PokerCardValue.values()) {
            if (v != PokerCardValue.JOKER && v.getNumber() == number) {
                return v;
            }
        }
        throw new IllegalArgumentException("Invalid card");
    }

    private ItalianCard<?> toItalianCard() {
        ItalianCardSuit suit = null;
        for (var s : ItalianCardSuit.values()) {
            if (s.toString().charAt(0) == card.charAt(0)) {
                suit = s;
                break;
            }
        }
        if (suit == null) {
            throw new IllegalArgumentException("Invalid card");
        }
        var value = parseNumber();
        for (var v : ItalianCardValue.values()) {
            if (v.getNumber() == value) {
                return new ItalianCard<>(suit, v);
            }
        }
        throw new IllegalArgumentException("Invalid card");
    }

    private int parseNumber() {
        try {
            return Integer.parseInt(card.substring(1));
        } catch (NumberFormatException | IndexOutOfBoundsException e) {
            throw new IllegalArgumentException("Invalid card");
        }
    }
}
