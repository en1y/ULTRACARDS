package com.ultracards.templates.game.interfaces;

import com.ultracards.templates.cards.AbstractCard;
import com.ultracards.templates.cards.CardTypeInterface;
import com.ultracards.templates.cards.CardValueInterface;
import com.ultracards.templates.game.exceptions.DeckException;
import com.ultracards.templates.game.model.AbstractDeck;
import com.ultracards.templates.game.model.AbstractHand;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public interface DeckInterface
        <CardType extends CardTypeInterface,
                CardValue extends CardValueInterface,
                Card extends AbstractCard<CardType, CardValue>,
                Hand extends AbstractHand<CardType, CardValue, Card>,
                Deck extends AbstractDeck<CardType, CardValue, Card, Hand>> {

    /* **** DEFAULT METHODS THAT ARE IMPLEMENTED **** */

    default void init(int size) {
        setSize(size);
        setCards(
                createCards(getSize())
        );
        shuffleCards(getCards());
        checkCardsLength();
    }

    default void checkCardsLength() {
        if (getCards().size() != getSize()) {
            throw new DeckException("AbstractDeck size should be %d. But instead it is %d", getSize(), getCards().size());
        }
    }

    default void shuffleCards(List<Card> cards) {
        Collections.shuffle(cards);
    }

    default Card drawCard() {
        decreaseSize();
        if (getSize() < 0) {
            throw new DeckException("Deck size should be >= 0 but instead it is %d", getSize());
        }
        return getCards().remove(0);
    };

    default List<Card> drawXCards(int cardsNum) {
        checkForSufficientNumberOfCards(cardsNum, "Can not draw %d cards since there are only %d cards left in the deck");

        var res = new ArrayList<Card>(cardsNum);
        for (int i = 0; i < cardsNum; i++) {
            res.add(drawCard());
        }

        return res;
    }

    default void checkForSufficientNumberOfCards(int cardsNum, String errorMessageToBeFormatted) {
        if (getSize() < cardsNum) {
            throw new DeckException(errorMessageToBeFormatted, cardsNum, getSize());
        }
    }

    default void decreaseSize() {
        setSize(getSize() - 1);
    }

    default boolean isEmpty() {
        return getSize() == 0;
    }

    /* **** METHODS THAT ARE NECESSARY **** */

    // init method
    List<Card> createCards(int size);

    // getHandFromDeck method
    Hand createHand(int cardsNum);

    // setters
    void setCards(List<Card> cards);
    void setSize(int size);

    // getters
    List<Card> getCards();
    int getSize();

    /* **** DEFAULT METHODS THAT ARE NOT NECESSARY **** */

    default List<Card> removeNotNeededCards() {return null;}
}
