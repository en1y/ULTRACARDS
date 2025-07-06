package com.ultracards.templates.game.interfaces;

import com.ultracards.templates.cards.AbstractCard;
import com.ultracards.templates.cards.CardTypeInterface;
import com.ultracards.templates.cards.CardValueInterface;
import com.ultracards.templates.game.model.AbstractDeck;
import com.ultracards.templates.game.model.AbstractHand;
import com.ultracards.templates.game.model.AbstractPlayer;

public interface GameInterface
        <CardType extends CardTypeInterface,
                CardValue extends CardValueInterface,
                Card extends AbstractCard<CardType, CardValue>,
                Hand extends AbstractHand<CardType, CardValue, Card>,
                Player extends AbstractPlayer<CardType, CardValue, Card, Hand>,
                Deck extends AbstractDeck<CardType, CardValue, Card>,
                PlayingField extends PlayingFieldInterface<CardType, CardValue, Card, Hand, Player>>{



}
