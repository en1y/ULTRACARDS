package com.ultracards.recorder;

import com.ultracards.templates.game.interfaces.GameInterface;
import com.ultracards.templates.game.interfaces.PlayingFieldInterface;
import com.ultracards.templates.game.model.AbstractPlayer;

import java.util.Map;

public interface GameRecordAttributes {
    GameRecordAttributes NONE = new GameRecordAttributes() {
    };

    default Map<String, String> gameAttributes(GameInterface<?, ?, ?, ?, ?, ?, ?> game) {
        return Map.of();
    }


    default Map<String, String> roundAttributes(
            PlayingFieldInterface<?, ?, ?, ?, ?, ?> playingField,
            AbstractPlayer<?, ?, ?, ?, ?> roundWinner) {
        return Map.of();
    }
}
