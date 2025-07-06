package com.ultracards.cards;

import com.ultracards.templates.cards.CardFactoryInterface;

import java.util.List;

public interface PokerCardFactoryInterface<Card extends PokerCard> extends CardFactoryInterface<PokerCardType, PokerCardValue, Card> {
    List<Card> create24CardDeck();
    List<Card> create36CardDeck();
    List<Card> create52CardDeck();

    @Override
    default List<Card> getCards() {
        return create52CardDeck();
    }
}
