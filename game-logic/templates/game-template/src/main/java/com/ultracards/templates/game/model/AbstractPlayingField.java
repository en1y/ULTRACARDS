package com.ultracards.templates.game.model;

import com.ultracards.templates.cards.AbstractCard;
import com.ultracards.templates.cards.CardSuitInterface;
import com.ultracards.templates.cards.CardValueInterface;
import com.ultracards.templates.game.interfaces.GameInterface;
import com.ultracards.templates.game.interfaces.PlayingFieldInterface;

import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

public abstract class AbstractPlayingField
        <CardType extends CardSuitInterface,
                CardValue extends CardValueInterface,
                Card extends AbstractCard<CardType, CardValue, Card>,
                Hand extends AbstractHand<CardType, CardValue, Card>,
                Deck extends AbstractDeck<CardType, CardValue, Card, Hand>,
                Player extends AbstractPlayer<CardType, CardValue, Card, Hand, Deck>>
        implements PlayingFieldInterface<CardType, CardValue, Card, Hand, Deck, Player> {

    private List<Card> cards;
    private List<Player> players;
    private Map<Player, Boolean> hasPlayerPlayed;
    private AbstractGame<CardType, CardValue, Card, Hand, Deck, Player, ?> game;

    public AbstractPlayingField(List<Player> players, AbstractGame<CardType, CardValue, Card, Hand, Deck, Player, ?> game) {
        init(players, game);
    }

    public List<Card> getPlayedCards() {
        return cards;
    }

    @Override
    public Map<Player, Boolean> getHasPlayerPlayed() {
        return hasPlayerPlayed;
    }

    @Override
    public GameInterface<CardType, CardValue, Card, Hand, Deck, Player, ?> getGame() {
        return game;
    }

    @Override
    public void setPlayedCards(List<Card> cards) {
        this.cards = cards;
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
    public void setHasPlayerPlayed(Map<Player, Boolean> hasPlayerPlayed) {
        this.hasPlayerPlayed = hasPlayerPlayed;
    }

    @Override
    public void setGame(GameInterface game) {
        this.game = (AbstractGame) game;
    }

    @Override
    public String toString() {
        var res = new StringJoiner(", ");

        for (var card : cards) {
            res.add(card.toString());
        }

        return "Played cards:[" + res + "]";
    }
}
