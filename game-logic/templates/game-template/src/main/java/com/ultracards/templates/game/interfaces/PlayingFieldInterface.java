package com.ultracards.templates.game.interfaces;

import com.ultracards.templates.cards.AbstractCard;
import com.ultracards.templates.cards.CardTypeInterface;
import com.ultracards.templates.cards.CardValueInterface;
import com.ultracards.templates.game.model.AbstractDeck;
import com.ultracards.templates.game.model.AbstractHand;
import com.ultracards.templates.game.model.AbstractPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public interface PlayingFieldInterface
        <CardType extends CardTypeInterface,
                CardValue extends CardValueInterface,
                Card extends AbstractCard<CardType, CardValue>,
                Hand extends AbstractHand<CardType, CardValue, Card>,
                Deck extends AbstractDeck<CardType, CardValue, Card, Hand>,
                Player extends AbstractPlayer<CardType, CardValue, Card, Hand, Deck>> {

    /* **** DEFAULT METHODS THAT ARE IMPLEMENTED **** */

    default void init() {
        setPlayedCards(new ArrayList<>());
        setPlayers(new ArrayList<>());
    }

    default void play(Card card, Player player) {
        Objects.requireNonNull(card, "card");
        Objects.requireNonNull(player, "player");
        addCard(card);
        player.playCard(card);
        addPlayer(player);
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

    // setters
    void setPlayedCards(List<Card> cards);
    void setPlayers(List<Player> players);

    /* **** DEFAULT METHODS THAT ARE NOT NECESSARY **** */

    // Hell is full.
}
