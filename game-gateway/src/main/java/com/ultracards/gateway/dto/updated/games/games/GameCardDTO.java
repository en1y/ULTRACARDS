package com.ultracards.gateway.dto.updated.games.games;

import com.ultracards.cards.*;
import com.ultracards.templates.cards.AbstractCard;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class GameCardDTO {
    private GameCardTypeDTO cardType;
    private String card;

    public static GameCardDTO createCardDTO(AbstractCard<?,?,?> card) {
        var str = Character.toString(card.getSuit().toString().charAt(0)) + card.getValue().getNumber();
        if (card instanceof PokerCard){
            return new GameCardDTO(GameCardTypeDTO.POKER, str);
        } else if (card instanceof ItalianCard){
            return new GameCardDTO(GameCardTypeDTO.ITALIAN, str);
        }
        return null;
    }

    public AbstractCard<?,?,?> toCard() {
        if (cardType.equals(GameCardTypeDTO.POKER)) {
            PokerCardSuit suit = null;
            for (var s: PokerCardSuit.values()) {
                if (s.getSuitName().charAt(0) == card.charAt(0)) {
                    suit = s;
                    break;
                }
            }
            if (suit != null) {
                var value = Integer.parseInt(card.substring(1));
                for (var v: PokerCardValue.values()) {
                    if (v.getNumber() == value) {
                        return new PokerCard<>(suit, v);
                    }
                }
            }
        }
        if (cardType.equals(GameCardTypeDTO.ITALIAN)) {
            ItalianCardSuit suit = null;
            for (var s: ItalianCardSuit.values()) {
                if (s.toString().charAt(0) == card.charAt(0)) {
                    suit = s;
                    break;
                }
            }
            if (suit != null) {
                var value = Integer.parseInt(card.substring(1));
                for (var v: ItalianCardValue.values()) {
                    if (v.getNumber() == value) {
                        return new ItalianCard<>(suit, v);
                    }
                }
            }
        }
        throw new IllegalArgumentException("Invalid card");
    }
}
enum GameCardTypeDTO {
    ITALIAN, POKER
}
