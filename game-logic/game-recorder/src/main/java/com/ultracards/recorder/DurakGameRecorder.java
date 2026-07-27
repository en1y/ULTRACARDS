package com.ultracards.recorder;

import com.ultracards.games.durak.DurakGameConfig;
import com.ultracards.games.durak.DurakPlayingField;
import com.ultracards.templates.game.interfaces.PlayingFieldInterface;
import com.ultracards.templates.game.model.AbstractPlayer;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.StringJoiner;
import java.util.UUID;
import java.util.function.Function;

/**
 * Records Durak. TAKE and DONE create no card plays; the bout's defender, opener, attack limit,
 * pass chain and DEFENDED/TAKEN outcome are stored as round attributes when the bout closes.
 */
public class DurakGameRecorder extends GameRecorder {

    public DurakGameRecorder(UUID id, UUID lobbyId, String name, Long ownerId, DurakGameConfig config,
                             Function<AbstractPlayer<?, ?, ?, ?, ?>, RecordedPlayer> playerMapper) {
        super(new RecordedDurakGame(id, lobbyId, name, ownerId, config.modeKey(), config.numberOfPlayers(),
                        config.deckSize(), config.jokersEnabled(), config.throwInPolicy().name(),
                        config.passingEnabled()),
                new DurakAttributes(), playerMapper);
    }

    @Override
    public RecordedDurakGame recording() {
        return (RecordedDurakGame) super.recording();
    }

    private static class DurakAttributes implements GameRecordAttributes {
        @Override
        public Map<String, String> roundAttributes(PlayingFieldInterface<?, ?, ?, ?, ?, ?> field,
                                                   AbstractPlayer<?, ?, ?, ?, ?> winner) {
            if (!(field instanceof DurakPlayingField bout)) return Map.of();
            var attributes = new LinkedHashMap<String, String>();
            attributes.put("bout", Integer.toString(bout.getBoutNumber()));
            attributes.put("outcome", bout.getOutcome() == null ? "UNRESOLVED" : bout.getOutcome().name());
            attributes.put("initialAttacker", bout.getInitialAttacker().getName());
            attributes.put("finalDefender", bout.getDefender().getName());
            attributes.put("maxAttackCards", Integer.toString(bout.getMaxAttackCards()));
            if (!bout.getPassChain().isEmpty()) {
                var chain = new StringJoiner(",");
                bout.getPassChain().forEach(player -> chain.add(player.getName()));
                attributes.put("passChain", chain.toString());
            }
            return attributes;
        }
    }
}
