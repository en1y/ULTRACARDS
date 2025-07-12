package com.ultracards.templates.game.observers;

import com.ultracards.templates.cards.AbstractCard;
import com.ultracards.templates.cards.CardTypeInterface;
import com.ultracards.templates.cards.CardValueInterface;
import com.ultracards.templates.game.model.AbstractHand;

public interface HandObserver <CardType extends CardTypeInterface,
        CardValue extends CardValueInterface,
        Card extends AbstractCard<CardType, CardValue>,
        Hand extends AbstractHand<CardType, CardValue, Card>> extends Observer<Hand> {
}
