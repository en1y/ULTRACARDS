package com.ultracards.recorder;

import com.ultracards.games.treseta.TresetaPlayer;
import com.ultracards.games.treseta.TresetaPlayingField;
import com.ultracards.templates.game.interfaces.GameInterface;
import com.ultracards.templates.game.interfaces.PlayingFieldInterface;
import com.ultracards.templates.game.model.AbstractPlayer;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.UUID;
import java.util.function.Function;

public class TresetaGameRecorder extends GameRecorder {
    public TresetaGameRecorder(UUID id, UUID lobbyId, String name, Long ownerId, String gameConfig,
                               boolean teamsEnabled, List<Long> teamUserIds,
                               Function<AbstractPlayer<?, ?, ?, ?, ?>, RecordedPlayer> playerMapper) {
        super(new RecordedTresetaGame(id, lobbyId, name, ownerId, gameConfig, teamsEnabled, teamUserIds),
                new TresetaAttributes(), playerMapper);
    }

    @Override
    public RecordedTresetaGame recording() {
        return (RecordedTresetaGame) super.recording();
    }

    private static class TresetaAttributes implements GameRecordAttributes {
        @Override
        public Map<String, String> roundAttributes(PlayingFieldInterface<?, ?, ?, ?, ?, ?> field,
                                                   AbstractPlayer<?, ?, ?, ?, ?> winner) {
            if (field instanceof TresetaPlayingField tresetaField)
                return Map.of("points", Integer.toString(tresetaField.calcTotalPoints()));
            return Map.of();
        }

        // Declarations are stored as game attributes ("declaration.N" ->
        // "playerName|TYPE|SUIT,SUIT,..."), so no extra tables are needed.
        @Override
        public Map<String, String> gameAttributes(GameInterface<?, ?, ?, ?, ?, ?, ?> game) {
            var attributes = new LinkedHashMap<String, String>();
            var index = 0;
            for (var raw : game.getPlayers()) {
                if (!(raw instanceof TresetaPlayer player)) continue;
                for (var declaration : player.getDeclarations()) {
                    var suits = new StringJoiner(",");
                    for (var suit : declaration.suits()) suits.add(suit.name());
                    attributes.put("declaration." + index++,
                            player.getName() + "|" + declaration.type().name() + "|" + suits);
                }
            }
            return attributes;
        }
    }
}
