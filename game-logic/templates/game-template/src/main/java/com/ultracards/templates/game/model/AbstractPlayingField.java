package com.ultracards.templates.game.model;

import com.ultracards.templates.cards.AbstractCard;
import com.ultracards.templates.cards.CardTypeInterface;
import com.ultracards.templates.cards.CardValueInterface;
import com.ultracards.templates.game.interfaces.PlayingFieldInterface;

import java.util.List;

public abstract class AbstractPlayingField
        <CardType extends CardTypeInterface,
                CardValue extends CardValueInterface,
                Card extends AbstractCard<CardType, CardValue, Card>,
                Hand extends AbstractHand<CardType, CardValue, Card>,
                Deck extends AbstractDeck<CardType, CardValue, Card, Hand>,
                Player extends AbstractPlayer<CardType, CardValue, Card, Hand, Deck>>
        implements PlayingFieldInterface<CardType, CardValue, Card, Hand, Deck, Player> {

    private List<Card> cards;
    private List<Player> players;

    public AbstractPlayingField() {
        init();
    }

    public List<Card> getPlayedCards() {
        return cards;
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
}
