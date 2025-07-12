package com.ultracards.templates.game.observers;

import com.ultracards.templates.cards.AbstractCard;
import com.ultracards.templates.cards.CardTypeInterface;
import com.ultracards.templates.cards.CardValueInterface;
import com.ultracards.templates.game.interfaces.PlayingFieldInterface;
import com.ultracards.templates.game.model.AbstractHand;
import com.ultracards.templates.game.model.AbstractPlayer;

public interface PlayingFieldObserver <CardType extends CardTypeInterface,
        CardValue extends CardValueInterface,
        Card extends AbstractCard<CardType, CardValue>,
        Hand extends AbstractHand<CardType, CardValue, Card>,
        Player extends AbstractPlayer<CardType, CardValue, Card, Hand>,
        PlayingField extends PlayingFieldInterface<CardType, CardValue, Card, Hand, Player>> extends Observer<PlayingField> {
}
