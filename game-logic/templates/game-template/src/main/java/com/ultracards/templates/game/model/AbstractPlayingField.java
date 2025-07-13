package com.ultracards.templates.game.model;

import com.ultracards.templates.cards.AbstractCard;
import com.ultracards.templates.cards.CardTypeInterface;
import com.ultracards.templates.cards.CardValueInterface;
import com.ultracards.templates.game.interfaces.PlayingFieldInterface;

public abstract class AbstractPlayingField
        <CardType extends CardTypeInterface,
                CardValue extends CardValueInterface,
                Card extends AbstractCard<CardType, CardValue>,
                Hand extends AbstractHand<CardType, CardValue, Card>,
                Deck extends AbstractDeck<CardType, CardValue, Card, Hand>,
                Player extends AbstractPlayer<CardType, CardValue, Card, Hand, Deck>>
        implements PlayingFieldInterface<CardType, CardValue, Card, Hand, Deck, Player> {
}
