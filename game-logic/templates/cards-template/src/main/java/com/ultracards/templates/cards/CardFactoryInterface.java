package com.ultracards.templates.cards;

import java.util.List;

public interface CardFactoryInterface
                <CardType extends CardSuitInterface,
                CardValue extends CardValueInterface,
                Card extends AbstractCard<CardType, CardValue, Card>> {
    List<Card> getCards();
}
