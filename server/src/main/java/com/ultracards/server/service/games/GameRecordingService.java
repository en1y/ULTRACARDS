package com.ultracards.server.service.games;

import com.ultracards.cards.ItalianCard;
import com.ultracards.cards.ItalianCardSuit;
import com.ultracards.cards.ItalianCardValue;
import com.ultracards.gateway.dto.games.games.GameCardDTO;
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
import com.ultracards.templates.game.model.AbstractGame;
import com.ultracards.templates.game.model.AbstractPlayer;
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
        game.setDiscardedCard(findDiscardedCard(game.getGame()));
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
        game.setDiscardedCard(findDiscardedCard(game.getGame()));
    }

    public void finish(TresetaGameEntity game) {
        var recorder = recorders.remove(game.getId());
        if (recorder == null) throw new IllegalStateException("No recorder attached");
        tresetaGameRepository.save((RecordedTresetaGame) recorder.recording());
    }

    static GameCardDTO findDiscardedCard(AbstractGame<?, ?, ?, ?, ?, ?, ?> game) {
        if (game.getNumberOfPlayers() != 3) return null;

        var cardsInPlay = new ArrayList<Object>(game.getDeck().getCards());
        for (var rawPlayer : game.getPlayers()) {
            var player = (AbstractPlayer<?, ?, ?, ?, ?>) rawPlayer;
            cardsInPlay.addAll(player.getHand().getCards());
        }
        for (var suit : ItalianCardSuit.values())
            for (var value : ItalianCardValue.values()) {
                var card = new ItalianCard<>(suit, value);
                if (!cardsInPlay.contains(card)) return GameCardDTO.createCardDTO(card);
            }
        return null;
    }
}
