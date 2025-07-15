package com.ultracards.templates.game.model;

import com.ultracards.templates.cards.AbstractCard;
import com.ultracards.templates.cards.CardTypeInterface;
import com.ultracards.templates.cards.CardValueInterface;
import com.ultracards.templates.game.interfaces.GameInterface;
import com.ultracards.templates.game.interfaces.PlayingFieldInterface;

import java.util.List;

public abstract class AbstractGame
        <CardType extends CardTypeInterface,
                CardValue extends CardValueInterface,
                Card extends AbstractCard<CardType, CardValue, Card>,
                Hand extends AbstractHand<CardType, CardValue, Card>,
                Deck extends AbstractDeck<CardType, CardValue, Card, Hand>,
                Player extends AbstractPlayer<CardType, CardValue, Card, Hand, Deck>,
                PlayingField extends PlayingFieldInterface<CardType, CardValue, Card, Hand, Deck, Player>>
        implements GameInterface <CardType, CardValue, Card, Hand, Deck, Player, PlayingField> {

    private Deck deck;
    private List<Player> players;
    private PlayingField playingField;
    private int numberOfPlayers;
    private int cardsNum;
    private int cardsInHandNum;

    public AbstractGame(int numberOfPlayers, int cardsNum, int cardsInHandNum) {
        init(numberOfPlayers, cardsNum, cardsInHandNum);
    }

    @Override
    public Deck getDeck() {
        return deck;
    }

    @Override
    public void setDeck(Deck deck) {
        this.deck = deck;
    }

    @Override
    public List<Player> getPlayers() {
        return players;
    }

    @Override
    public void setPlayers(List<Player> players) {
        this.players = players;
    }

    @Override
    public PlayingField getPlayingField() {
        return playingField;
    }

    @Override
    public void setPlayingField(PlayingField playingField) {
        this.playingField = playingField;
    }

    @Override
    public int getNumberOfPlayers() {
        return numberOfPlayers;
    }

    @Override
    public void setNumberOfPlayers(int numberOfPlayers) {
        this.numberOfPlayers = numberOfPlayers;
    }

    @Override
    public int getCardsNum() {
        return cardsNum;
    }

    @Override
    public void setCardsNum(int cardsNum) {
        this.cardsNum = cardsNum;
    }

    @Override
    public int getCardsInHandNum() {
        return cardsInHandNum;
    }

    @Override
    public void setCardsInHandNum(int cardsInHandNum) {
        this.cardsInHandNum = cardsInHandNum;
    }
}
