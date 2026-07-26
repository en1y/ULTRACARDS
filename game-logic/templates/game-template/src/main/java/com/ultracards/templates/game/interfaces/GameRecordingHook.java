package com.ultracards.templates.game.interfaces;

import com.ultracards.templates.cards.AbstractCard;
import com.ultracards.templates.game.model.AbstractPlayer;

import java.util.List;

public interface GameRecordingHook {
    GameRecordingHook NONE = new GameRecordingHook() {};

    default void gameStarted(GameInterface<?, ?, ?, ?, ?, ?, ?> game) {}

    default void roundStarted(PlayingFieldInterface<?, ?, ?, ?, ?, ?> playingField) {}

    default void cardPlayed(
            PlayingFieldInterface<?, ?, ?, ?, ?, ?> playingField,
            AbstractPlayer<?, ?, ?, ?, ?> player,
            AbstractCard<?, ?, ?> card) {}

    /**
     * Card play with an explicit role, for games where a card is not simply "the player's turn".
     * Durak uses ATTACK/DEFEND/THROW_IN/PASS and points a defense at the attack it covers;
     * trick games keep the plain overload and are recorded as PLAY.
     *
     * @param actionType       role of this card play
     * @param targetPlayOrder  zero-based play order this card responds to, or {@code null}
     */
    default void cardPlayed(
            PlayingFieldInterface<?, ?, ?, ?, ?, ?> playingField,
            AbstractPlayer<?, ?, ?, ?, ?> player,
            AbstractCard<?, ?, ?> card,
            String actionType,
            Integer targetPlayOrder) {
        cardPlayed(playingField, player, card);
    }

    default void roundEnded(
            PlayingFieldInterface<?, ?, ?, ?, ?, ?> playingField,
            AbstractPlayer<?, ?, ?, ?, ?> roundWinner) {}

    default void gameEnded(
            GameInterface<?, ?, ?, ?, ?, ?, ?> game,
            List<? extends AbstractPlayer<?, ?, ?, ?, ?>> winners) {}
}
