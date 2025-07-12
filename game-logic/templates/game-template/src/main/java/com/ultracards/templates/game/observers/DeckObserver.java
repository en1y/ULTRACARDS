package com.ultracards.templates.game.observers;

import com.ultracards.templates.cards.AbstractCard;
import com.ultracards.templates.cards.CardTypeInterface;
import com.ultracards.templates.cards.CardValueInterface;
import com.ultracards.templates.game.model.AbstractDeck;

public interface DeckObserver <CardType extends CardTypeInterface,
        CardValue extends CardValueInterface,
        Card extends AbstractCard<CardType, CardValue>,
        Deck extends AbstractDeck<CardType, CardValue, Card>> extends Observer<Deck> {
}
