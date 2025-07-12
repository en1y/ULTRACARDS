package com.ultracards.templates.game.observers;

import com.ultracards.templates.cards.AbstractCard;
import com.ultracards.templates.cards.CardTypeInterface;
import com.ultracards.templates.cards.CardValueInterface;
import com.ultracards.templates.game.interfaces.PlayingFieldInterface;
import com.ultracards.templates.game.model.AbstractDeck;
import com.ultracards.templates.game.model.AbstractHand;
import com.ultracards.templates.game.model.AbstractPlayer;
import com.ultracards.templates.game.model.AbstractPlayingField;

public class GameObserver<CardType extends CardTypeInterface,
        CardValue extends CardValueInterface,
        Card extends AbstractCard<CardType, CardValue>,
        Hand extends AbstractHand<CardType, CardValue, Card>,
        Player extends AbstractPlayer<CardType, CardValue, Card, Hand>,
        Deck extends AbstractDeck<CardType, CardValue, Card>,
        PlayingField extends PlayingFieldInterface<CardType, CardValue, Card, Hand, Player>>
        implements Observer<Object> {

    private final CardObserver<CardType, CardValue, Card> cardObserver;
    private final DeckObserver<CardType, CardValue, Card, Deck> deckObserver;
    private final HandObserver<CardType, CardValue, Card, Hand> handObserver;
    private final PlayerObserver<CardType, CardValue, Card, Hand, Player> playerObserver;
    private final PlayingFieldObserver<CardType, CardValue, Card, Hand, Player, PlayingField> playingFieldObserver;

    private final Class<Card> cardClass;
    private final Class<Deck> deckClass;
    private final Class<Hand> handClass;
    private final Class<Player> playerClass;
    private final Class<PlayingField> playingFieldClass;

    public GameObserver(
            CardObserver<CardType, CardValue, Card> cardObserver,
            DeckObserver<CardType, CardValue, Card, Deck> deckObserver,
            HandObserver<CardType, CardValue, Card, Hand> handObserver,
            PlayerObserver<CardType, CardValue, Card, Hand, Player> playerObserver,
            PlayingFieldObserver<CardType, CardValue, Card, Hand, Player, PlayingField> playingFieldObserver,
            Class<Card> cardClass,
            Class<Deck> deckClass,
            Class<Hand> handClass,
            Class<Player> playerClass,
            Class<PlayingField> playingFieldClass
    ) {
        this.cardObserver = cardObserver;
        this.deckObserver = deckObserver;
        this.handObserver = handObserver;
        this.playerObserver = playerObserver;
        this.playingFieldObserver = playingFieldObserver;
        this.cardClass = cardClass;
        this.deckClass = deckClass;
        this.handClass = handClass;
        this.playerClass = playerClass;
        this.playingFieldClass = playingFieldClass;
    }

    @Override
    public void update(Object event) {
        if (cardClass.isInstance(event)) {
            cardObserver.update(cardClass.cast(event));
        } else if (deckClass.isInstance(event)) {
            deckObserver.update(deckClass.cast(event));
        } else if (handClass.isInstance(event)) {
            handObserver.update(handClass.cast(event));
        } else if (playerClass.isInstance(event)) {
            playerObserver.update(playerClass.cast(event));
        } else if (playingFieldClass.isInstance(event)) {
            playingFieldObserver.update(playingFieldClass.cast(event));
        } else {
            throw new IllegalArgumentException("Unknown event type: " + event.getClass().getName());
        }
    }

}
