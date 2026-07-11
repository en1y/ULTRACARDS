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

    default void roundEnded(
            PlayingFieldInterface<?, ?, ?, ?, ?, ?> playingField,
            AbstractPlayer<?, ?, ?, ?, ?> roundWinner) {}

    default void gameEnded(
            GameInterface<?, ?, ?, ?, ?, ?, ?> game,
            List<? extends AbstractPlayer<?, ?, ?, ?, ?>> winners) {}
}
