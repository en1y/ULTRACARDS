package com.ultracards.server.service.games;

import com.ultracards.recorder.BriskulaGameRecorder;
import com.ultracards.recorder.GameRecorder;
import com.ultracards.recorder.RecordedBriskulaGame;
import com.ultracards.recorder.RecordedPlayer;
import com.ultracards.recorder.RecordedTresetaGame;
import com.ultracards.recorder.TresetaGameRecorder;
import com.ultracards.server.entity.games.briskula.BriskulaGameEntity;
import com.ultracards.server.entity.games.briskula.BriskulaPlayerEntity;
import com.ultracards.server.entity.games.treseta.TresetaGameEntity;
import com.ultracards.server.entity.games.treseta.TresetaPlayerEntity;
import com.ultracards.server.repositories.games.BriskulaGameRepository;
import com.ultracards.server.repositories.games.TresetaGameRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class GameRecordingService {
    private final BriskulaGameRepository briskulaGameRepository;
    private final TresetaGameRepository tresetaGameRepository;
    private final Map<UUID, GameRecorder> recorders = new ConcurrentHashMap<>();

    public void start(BriskulaGameEntity game) {
        var teamIds = new ArrayList<Long>();
        for (var player : game.getTeamPlayers()) {
            teamIds.add(player.getId());
        }
        var recorder = new BriskulaGameRecorder(game.getId(), game.getLobbyId(), game.getName(), game.getOwner().getId(),
                game.getPersistedGameConfig().name(), game.getPersistedGameConfig().areTeamsEnabled(), teamIds, player -> {
                    var briskulaPlayer = (BriskulaPlayerEntity) player;
                    return new RecordedPlayer(briskulaPlayer.getUser().getId(), briskulaPlayer.getName());
                });
        recorders.put(game.getId(), recorder);
        recorder.attach(game.getGame());
        game.getGame().start();
    }

    public void finish(BriskulaGameEntity game) {
        var recorder = recorders.remove(game.getId());
        if (recorder == null) {
            throw new IllegalStateException("No recorder attached");
        }
        briskulaGameRepository.save((RecordedBriskulaGame) recorder.recording());
    }

    public void start(TresetaGameEntity game) {
        var teamIds = new ArrayList<Long>();
        for (var player : game.getTeamPlayers()) teamIds.add(player.getId());
        var recorder = new TresetaGameRecorder(game.getId(), game.getLobbyId(), game.getName(), game.getOwner().getId(),
                game.getPersistedGameConfig().name(), game.getPersistedGameConfig().areTeamsEnabled(), teamIds, player -> {
                    var tresetaPlayer = (TresetaPlayerEntity) player;
                    return new RecordedPlayer(tresetaPlayer.getUser().getId(), tresetaPlayer.getName());
                });
        recorders.put(game.getId(), recorder);
        recorder.attach(game.getGame());
        game.getGame().start();
    }

    public void finish(TresetaGameEntity game) {
        var recorder = recorders.remove(game.getId());
        if (recorder == null) throw new IllegalStateException("No recorder attached");
        tresetaGameRepository.save((RecordedTresetaGame) recorder.recording());
    }
}
