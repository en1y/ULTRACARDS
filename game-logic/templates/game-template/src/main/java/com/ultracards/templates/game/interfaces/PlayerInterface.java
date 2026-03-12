package com.ultracards.templates.game.interfaces;

import com.ultracards.templates.cards.AbstractCard;
import com.ultracards.templates.cards.CardSuitInterface;
import com.ultracards.templates.cards.CardValueInterface;
import com.ultracards.templates.game.model.AbstractDeck;
import com.ultracards.templates.game.model.AbstractHand;

import java.util.Objects;

public interface PlayerInterface
        <CardType extends CardSuitInterface,
                CardValue extends CardValueInterface,
                Card extends AbstractCard<CardType, CardValue, Card>,
                Hand extends AbstractHand<CardType, CardValue, Card>,
                Deck extends AbstractDeck<CardType, CardValue, Card, Hand>> {

    /* **** DEFAULT METHODS THAT ARE IMPLEMENTED **** */

    default void init (String name) {
        setName(name);
    }

    default void getHandFromDeck(Deck deck, int cardsInHandNumber) {
        Objects.requireNonNull(deck, "deck");
        setHand(
                deck.createHand(cardsInHandNumber)
        );
    }

    default Card playCard(Card card) {
        if (getHand().getCards().contains(card)){
            return getHand().drawCard(card);
        }
        throw new IllegalStateException("There is no such card in the hand");
    }

    /* **** METHODS THAT ARE NECESSARY **** */

    // setter
    void setHand(Hand hand);
    void setName(String name);

    // getters
    String getName();
    Hand getHand();

    /* **** DEFAULT METHODS THAT ARE NOT NECESSARY **** */

    // But nobody came.
}
