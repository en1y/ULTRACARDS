package com.ultracards.templates.game.interfaces;

import com.ultracards.templates.cards.AbstractCard;
import com.ultracards.templates.cards.CardTypeInterface;
import com.ultracards.templates.cards.CardValueInterface;
import com.ultracards.templates.game.model.AbstractDeck;
import com.ultracards.templates.game.model.AbstractHand;
import com.ultracards.templates.game.model.AbstractPlayer;
import com.ultracards.templates.game.observers.Observer;

import java.util.List;

public interface GameInterface
        <CardType extends CardTypeInterface,
                CardValue extends CardValueInterface,
                Card extends AbstractCard<CardType, CardValue>,
                Hand extends AbstractHand<CardType, CardValue, Card>,
                Player extends AbstractPlayer<CardType, CardValue, Card, Hand>,
                Deck extends AbstractDeck<CardType, CardValue, Card>,
                PlayingField extends PlayingFieldInterface<CardType, CardValue, Card, Hand, Player>> {

    default void init(int numberOfPlayers, int cardsNum, int cardsInHandNum, List<Player> players) {
        setNumberOfPlayers(numberOfPlayers);
        setCardsNum(cardsNum);
        setCardsInHandNum(cardsInHandNum);
        setPlayers(players);
        preGameCreateCheck(numberOfPlayers, cardsNum);
        deckSetUp();
    }

    void preGameCreateCheck(int numberOfPlayers, int cardsNum);

    default void start() {
        if (getPlayers().isEmpty()) {
            playersSetUp();
        }
        gameStart();
        roundCycle();
        gameEnd();
    }

    void restart();

    void deckSetUp();

    List<Player> getPlayers();

    void setNumberOfPlayers(int numberOfPlayers);
    void setCardsNum(int cardsNum);
    void setCardsInHandNum(int cardsInHandNum);
    void setPlayers(List<Player> players);
}
