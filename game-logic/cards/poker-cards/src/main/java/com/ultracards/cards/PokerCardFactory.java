package com.ultracards.cards;

import com.ultracards.templates.cards.CardFactoryInterface;

import java.util.ArrayList;
import java.util.List;

public class PokerCardFactory implements PokerCardFactoryInterface<PokerCard> {

    @Override
    public List<PokerCard> create24CardDeck() {
        return createXToAceDeck(9);
    }

    @Override
    public List<PokerCard> create36CardDeck() {
        return createXToAceDeck(6);
    }

    @Override
    public List<PokerCard> create52CardDeck() {
        return createXToAceDeck(2);
    }

    private List<PokerCard> createXToAceDeck(int startNumber) {
        var res = new ArrayList<PokerCard>();

        var cardTypes = PokerCardType.values();
        var cardValues = PokerCardValue.values();

        for (var type : cardTypes) {
            for (var value : cardValues) {
                if (value.getNumber() >= startNumber) {
                    res.add(new PokerCard(type, value));
                }
            }
        }

        return res;
    }
}
