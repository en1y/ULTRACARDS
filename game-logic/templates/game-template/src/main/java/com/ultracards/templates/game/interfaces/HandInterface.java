package com.ultracards.templates.game.interfaces;

import com.ultracards.templates.cards.AbstractCard;
import com.ultracards.templates.cards.CardTypeInterface;
import com.ultracards.templates.cards.CardValueInterface;
import com.ultracards.templates.game.exceptions.HandException;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public interface HandInterface
        <CardType extends CardTypeInterface,
                CardValue extends CardValueInterface,
                Card extends AbstractCard<CardType, CardValue>> {

    default void init(int capacity) {
        if (capacity <= 0) {
            throw new HandException("Hand capacity: %d, can not be less than 1", capacity);
        }
        setCapacity(capacity);
        setCardsNum(0);
        setCards(new ArrayList<>(getCapacity()));
    }

    default void validateCardsNumber(int newCardsNum) {
        if (newCardsNum > getCapacity()) {
            throw new HandException("Can not add card/s because the new cards in hand num: " + (newCardsNum + 1) + " is higher than the capacity: " + getCapacity());
        }
    }

    default void addCard(Card card) {
        Objects.requireNonNull(card, "Card can not be null");

        validateCardsNumber(getCardsNum() + 1);
        getCards().add(card);
        setCardsNum(getCardsNum() + 1);
    }

    default void addCards(List<Card> cards) {
        Objects.requireNonNull(cards, "Cards can not be null");

        for(var card : cards) {
            addCard(card);
        }
    }

    default Card drawCard(Card card) {
        Objects.requireNonNull(card, "Card can not be null");

        if (!getCards().remove(card)) {
            throw new HandException("Card not found in hand: " + card);
        }
        setCardsNum(getCardsNum() - 1);
        return card;
    }

    boolean isEmpty();

    List<Card> getCards();
    int getCapacity();
    int getCardsNum();

    void setCards(List<Card> cards);
    void setCapacity(int capacity);
    void setCardsNum(int cardsNum);
}
