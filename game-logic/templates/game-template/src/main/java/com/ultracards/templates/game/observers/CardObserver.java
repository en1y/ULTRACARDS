package com.ultracards.templates.game.observers;

import com.ultracards.templates.cards.AbstractCard;
import com.ultracards.templates.cards.CardTypeInterface;
import com.ultracards.templates.cards.CardValueInterface;

public interface CardObserver<CardType extends CardTypeInterface,
        CardValue extends CardValueInterface,
        Card extends AbstractCard<CardType, CardValue>> extends Observer<Card> {
}
