package com.ultracards.templates.game.interfaces;

import com.ultracards.templates.cards.AbstractCard;
import com.ultracards.templates.cards.CardTypeInterface;
import com.ultracards.templates.cards.CardValueInterface;
import com.ultracards.templates.game.model.AbstractDeck;
import com.ultracards.templates.game.model.AbstractHand;
import com.ultracards.templates.game.model.AbstractPlayer;
import com.ultracards.templates.game.observers.GameSubject;

import java.util.ArrayList;
import java.util.List;

public interface GameInterface
        <CardType extends CardTypeInterface,
                CardValue extends CardValueInterface,
                Card extends AbstractCard<CardType, CardValue>,
                Hand extends AbstractHand<CardType, CardValue, Card>,
                Player extends AbstractPlayer<CardType, CardValue, Card, Hand>,
                Deck extends AbstractDeck<CardType, CardValue, Card, Hand>,
                PlayingField extends PlayingFieldInterface<CardType, CardValue, Card, Hand, Player>>
        extends GameSubject {

    default void init(int numberOfPlayers, int cardsNum, int cardsInHandNum) {
        setNumberOfPlayers(numberOfPlayers);
        setPlayers(new ArrayList<>(getNumberOfPlayers()));
        setCardsNum(cardsNum);
        setCardsInHandNum(cardsInHandNum);
        setPlayingField(null);
        preGameCreateCheck(numberOfPlayers, cardsNum);
    }

    void preGameCreateCheck(int numberOfPlayers, int cardsNum);

    default void start() {
        setDeck(createDeck(getCardsNum()));
        var removedCards = removeNotNeededCards(getDeck(), getCardsInHandNum());
        handleRemovedCards(removedCards);
        var players = createPlayers();
        if (players.size() < getNumberOfPlayers()) {
            throw new IllegalArgumentException("Not enough players created. Expected: " + getNumberOfPlayers() + ", but got: " + players.size());
        }
        setPlayers(players);
        createPlayersHands(getDeck(), getPlayers());
        setNumberOfPlayers(getPlayers().size());
        gameStart();
        roundCycle();
        gameEnd();
    }

    void createPlayersHands(Deck deck, List<Player> players);

    default void restart() {
        setDeck(
                createDeck(getCardsNum())
        );
    }

    default void gameStart(){}
    default void roundCycle(){
        while (isGameActive(getDeck(), getPlayers())) {
            roundStart();
            setPlayingField(
                    createPlayingField()
            );
            playTurn(getPlayingField(), getPlayers());
            var roundWinner = determineRoundWinner(getPlayingField());
            postRoundWinnerDeterminedActions(roundWinner, getPlayingField());
            drawCards(getPlayers(), getDeck());
            roundEnd(getPlayingField(), roundWinner);
        }
        setPlayingField(null);
    }

    default void drawCards(List<Player> players, Deck deck) {}
    default void roundStart() {}
    default void roundEnd(PlayingField playingField, Player roundWinner) {}

    default void gameEnd() {
        preGameEnd();
        var winners = determineGameWinners(getPlayers());
        postGameWinnersDeterminedActions(winners);
        postGameEnd();
    }

    void postGameEnd();

    default void postGameWinnersDeterminedActions(List<Player> winners) {};
    default List<Player> determineGameWinners(List<Player> players) {
        return new ArrayList<>();
    }
    void preGameEnd();

    default void postRoundWinnerDeterminedActions(Player roundWinner, PlayingField playingField) {}


    default List<Card> removeNotNeededCards(Deck deck, int cardsInHandNum) {
        return new ArrayList<>();
    }
    default void handleRemovedCards(List<Card> removedCards) {
        // Default implementation can be empty or can log the removed cards
    }

    // player management methods
    default void addPlayer(Player player) {
        Objects.requireNonNull(player, "player must not be null");
        getPlayers().add(player);
    }

    void playTurn(PlayingField playingField, List<Player> players);

    PlayingField createPlayingField();

    boolean isGameActive(Deck deck, List<Player> players);

    List<Player> createPlayers();

    default void preCreatePlayers() {};

    Deck createDeck(int cardsNum);

    int getNumberOfPlayers();
    int getCardsNum();
    int getCardsInHandNum();
    List<Player> getPlayers();
    Deck getDeck();
    PlayingField getPlayingField();

    void setNumberOfPlayers(int numberOfPlayers);
    void setCardsNum(int cardsNum);
    void setCardsInHandNum(int cardsInHandNum);
    void setPlayers(List<Player> players);
    void setDeck(Deck deck);
    void setPlayingField(PlayingField playingField);

    void addPlayer(Player player);
    void addPlayers(List<Player> player);
}
