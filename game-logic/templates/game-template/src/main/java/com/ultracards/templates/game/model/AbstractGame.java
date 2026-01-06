package com.ultracards.templates.game.model;

import com.ultracards.templates.cards.AbstractCard;
import com.ultracards.templates.cards.CardSuitInterface;
import com.ultracards.templates.cards.CardValueInterface;
import com.ultracards.templates.game.interfaces.GameInterface;

import java.util.List;

public abstract class AbstractGame
        <CardType extends CardSuitInterface,
                CardValue extends CardValueInterface,
                Card extends AbstractCard<CardType, CardValue, Card>,
                Hand extends AbstractHand<CardType, CardValue, Card>,
                Deck extends AbstractDeck<CardType, CardValue, Card, Hand>,
                Player extends AbstractPlayer<CardType, CardValue, Card, Hand, Deck>,
                PlayingField extends AbstractPlayingField<CardType, CardValue, Card, Hand, Deck, Player>>
        implements GameInterface <CardType, CardValue, Card, Hand, Deck, Player, PlayingField> {

    private Deck deck;
    private List<Player> players;
    private PlayingField playingField;
    private int numberOfPlayers;
    private int cardsNum;
    private int cardsInHandNum;

    public AbstractGame(List<Player> players, int cardsNum, int cardsInHandNum) {
        init(players, cardsNum, cardsInHandNum);
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

    @Override
    public String toString() {
        var res = new StringBuilder();
        res.append("players:\n");
        for (var player : players) {
            res.append("- ").append(player.toString()).append("\n");
        }
        res.append(playingField.toString()).append("\n");
        return res.toString();
    }
}
