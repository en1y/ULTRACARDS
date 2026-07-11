package com.ultracards.recorder;

import com.ultracards.games.briskula.BriskulaGame;
import com.ultracards.games.briskula.BriskulaPlayingField;
import com.ultracards.templates.game.interfaces.GameInterface;
import com.ultracards.templates.game.interfaces.PlayingFieldInterface;
import com.ultracards.templates.game.model.AbstractPlayer;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

public class BriskulaGameRecorder extends GameRecorder {
    public BriskulaGameRecorder(UUID id, UUID lobbyId, String name, Long ownerId, String gameConfig, boolean teamsEnabled,
                                List<Long> teamUserIds, Function<AbstractPlayer<?, ?, ?, ?, ?>, RecordedPlayer> playerMapper) {
        super(new RecordedBriskulaGame(id, lobbyId, name, ownerId, gameConfig, teamsEnabled, teamUserIds),
                new BriskulaAttributes(), playerMapper);
    }

    @Override
    public void gameStarted(GameInterface<?, ?, ?, ?, ?, ?, ?> game) {
        super.gameStarted(game);
        if (game instanceof BriskulaGame briskulaGame) {
            recording().setTrump(briskulaGame.getGameTrumpCard().getSuit().name(), briskulaGame.getGameTrumpCard().getValue().name());
        }
    }

    @Override
    public RecordedBriskulaGame recording() {
        return (RecordedBriskulaGame) super.recording();
    }

    private static class BriskulaAttributes implements GameRecordAttributes {
        @Override
        public Map<String, String> roundAttributes(PlayingFieldInterface<?, ?, ?, ?, ?, ?> field,
                                                   AbstractPlayer<?, ?, ?, ?, ?> winner) {
            if (field instanceof BriskulaPlayingField briskulaField) {
                return Map.of("points", Integer.toString(briskulaField.calcTotalPoints()));
            }
            return Map.of();
        }
    }
}
