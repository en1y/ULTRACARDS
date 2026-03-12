package com.ultracards.templates.game.interfaces;

import com.ultracards.templates.cards.AbstractCard;
import com.ultracards.templates.cards.CardSuitInterface;
import com.ultracards.templates.cards.CardValueInterface;
import com.ultracards.templates.game.model.AbstractDeck;
import com.ultracards.templates.game.model.AbstractHand;
import com.ultracards.templates.game.model.AbstractPlayer;

import java.util.*;

public interface PlayingFieldInterface
        <CardType extends CardSuitInterface,
                CardValue extends CardValueInterface,
                Card extends AbstractCard<CardType, CardValue, Card>,
                Hand extends AbstractHand<CardType, CardValue, Card>,
                Deck extends AbstractDeck<CardType, CardValue, Card, Hand>,
                Player extends AbstractPlayer<CardType, CardValue, Card, Hand, Deck>> {

    /* **** DEFAULT METHODS THAT ARE IMPLEMENTED **** */

    default void init(List<Player> players, GameInterface<CardType, CardValue, Card, Hand, Deck, Player, ?> game) {
        setPlayedCards(new ArrayList<>());
        setPlayers(players);
        var hasPlayerPlayed = new HashMap<Player, Boolean>();
        for (Player player : players) {
            hasPlayerPlayed.put(player, false);
        }
        setHasPlayerPlayed(hasPlayerPlayed);
        setGame(game);
    }

    default Player getCurrentPlayer() {
        var map = getHasPlayerPlayed();
        for (var player : getPlayers()) {
            if (!map.get(player)) {
                return player;
            }
        }
        return null;
    }

    default boolean isTurnPlayed() {
        var map = getHasPlayerPlayed();
        for (var player : getPlayers()) {
            if (!map.get(player)) {
                return false;
            }
        }
        return true;
    }

    default void play(Card card, Player player) {
        Objects.requireNonNull(card, "card");
        Objects.requireNonNull(player, "player");
        addCard(card);
        getHasPlayerPlayed().put(player, true);
        player.playCard(card);
        if (isTurnPlayed()) {
            getGame().roundCycle();
        }
    }

    default Player getPlayerByPlayedCard(Card card) {
        Objects.requireNonNull(card, "card");

        var index = getPlayedCards().indexOf(card);
        if (index < 0 || index >= getPlayers().size()) {
            throw new IllegalArgumentException("Card not found in played cards or no players available.");
        }

        return getPlayers().get(index);
    }

    default Card getPlayedCardByPlayer(Player player) {
        Objects.requireNonNull(player, "player");

        var index = getPlayers().indexOf(player);
        if (index < 0 || index >= getPlayedCards().size()) {
            throw new IllegalArgumentException("Player not found in players or no cards available.");
        }

        return getPlayedCards().get(index);
    }

    default void addPlayer (Player player) {
        getPlayers().add(player);
    }

    default void addCard (Card card) {
        getPlayedCards().add(card);
    }

    /* **** METHODS THAT ARE NECESSARY **** */

    // GameInterface.determineRoundWinner() methods
    Player determineRoundWinner();

    // getters
    List<Card> getPlayedCards();
    List<Player> getPlayers();
    Map<Player, Boolean> getHasPlayerPlayed();
    GameInterface<CardType, CardValue, Card, Hand, Deck, Player, ?> getGame();

    // setters
    void setPlayedCards(List<Card> cards);
    void setPlayers(List<Player> players);
    void setHasPlayerPlayed(Map<Player, Boolean> hasPlayerPlayed);
    void setGame(GameInterface<CardType, CardValue, Card, Hand, Deck, Player, ?> game);

    /* **** DEFAULT METHODS THAT ARE NOT NECESSARY **** */

    // Hell is full.
}
