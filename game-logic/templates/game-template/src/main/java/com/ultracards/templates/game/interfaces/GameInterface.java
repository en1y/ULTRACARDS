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
import java.util.Objects;

public interface GameInterface
        <CardType extends CardTypeInterface,
                CardValue extends CardValueInterface,
                Card extends AbstractCard<CardType, CardValue>,
                Hand extends AbstractHand<CardType, CardValue, Card>,
                Player extends AbstractPlayer<CardType, CardValue, Card, Hand>,
                Deck extends AbstractDeck<CardType, CardValue, Card, Hand>,
                PlayingField extends PlayingFieldInterface<CardType, CardValue, Card, Hand, Player>>
        extends GameSubject {

    /***** DEFAULT METHODS THAT ARE IMPLEMENTED *****/

    default void init(int numberOfPlayers, int cardsNum, int cardsInHandNum) {
        setNumberOfPlayers(numberOfPlayers);
        setPlayers(new ArrayList<>(getNumberOfPlayers()));
        setCardsNum(cardsNum);
        setCardsInHandNum(cardsInHandNum);
        setPlayingField(null);
        preGameCreateCheck(numberOfPlayers, cardsNum);
    }

    default void start() {
        setDeck(createDeck(getCardsNum()));
        removeNotNeededCards(getDeck(), getCardsInHandNum());
        var players = createPlayers();
        if (players.size() < getNumberOfPlayers()) {
            throw new IllegalArgumentException("Not enough players created. Expected: " + getNumberOfPlayers() + ", but got: " + players.size());
        }
        setPlayers(players);
        createPlayersHands(getDeck(), getPlayers());
        setNumberOfPlayers(getPlayers().size());
        while (isGameActive(getDeck(), getPlayers())) {
            roundCycle();
        }
        setPlayingField(null);
        gameEnd();
    }

    default void restart() {
        setDeck(
                createDeck(getCardsNum())
        );
        // TODO implement the restart logic
    }

    default void roundCycle(){
        setPlayingField(
                createPlayingField()
        );
        playTurn(getPlayingField(), getPlayers());
        var roundWinner = determineRoundWinner(getPlayingField());
        postRoundWinnerDeterminedActions(roundWinner, getPlayingField());
        drawCards(getPlayers(), getDeck());
        roundEnd(getPlayingField(), roundWinner);
    }

    default void gameEnd() {
        var winners = determineGameWinners(getPlayers());
        postGameWinnersDeterminedActions(winners);
    }

    default Player determineRoundWinner(PlayingField playingField) {
        return playingField.determineRoundWinner();
    }

    /***** METHODS THAT ARE NECESSARY *****/

    // start methods
    Deck createDeck(int cardsNum);

    //roundCycle methods
    PlayingField createPlayingField();
    void playTurn(PlayingField playingField, List<Player> players);

    // getters
    int getNumberOfPlayers();
    int getCardsNum();
    int getCardsInHandNum();
    List<Player> getPlayers();
    Deck getDeck();
    PlayingField getPlayingField();

    // setters
    void setNumberOfPlayers(int numberOfPlayers);
    void setCardsNum(int cardsNum);
    void setCardsInHandNum(int cardsInHandNum);
    void setPlayers(List<Player> players);
    void setDeck(Deck deck);
    void setPlayingField(PlayingField playingField);

    /***** DEFAULT METHODS THAT ARE NOT NECESSARY *****/

    // init methods
    void preGameCreateCheck(int numberOfPlayers, int cardsNum);

    // start methods
    default List<Card> removeNotNeededCards(Deck deck, int cardsInHandNum) {return new ArrayList<>();}
    void createPlayersHands(Deck deck, List<Player> players);
    List<Player> createPlayers();
    boolean isGameActive(Deck deck, List<Player> players);

    // roundCycle methods
    default void drawCards(List<Player> players, Deck deck) {}
    default void roundEnd(PlayingField playingField, Player roundWinner) {}
    default void postRoundWinnerDeterminedActions(Player roundWinner, PlayingField playingField) {}

    // gameEnd methods
    default List<Player> determineGameWinners(List<Player> players) {return new ArrayList<>();}
    default void postGameWinnersDeterminedActions(List<Player> winners) {}

    // player management methods
    default void addPlayer(Player player) {
        Objects.requireNonNull(player, "player must not be null");
        getPlayers().add(player);
    }
    default void addPlayers(List<Player> players) {
        Objects.requireNonNull(players, "players must not be null");
        getPlayers().addAll(players);
    }
}
