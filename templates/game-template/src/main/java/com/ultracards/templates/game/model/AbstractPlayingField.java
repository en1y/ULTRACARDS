package com.ultracards.templates.game.model;

import com.ultracards.templates.cards.AbstractCard;
import com.ultracards.templates.cards.CardTypeInterface;
import com.ultracards.templates.cards.CardValueInterface;
import com.ultracards.templates.game.interfaces.PlayingFieldInterface;

public abstract class AbstractPlayingField
        <CardType extends CardTypeInterface,
                CardValue extends CardValueInterface,
                Card extends AbstractCard<CardType, CardValue>,
                Player extends AbstractPlayer<CardType, CardValue, Card>>
        implements PlayingFieldInterface<CardType, CardValue, Card, Player> {
}
