package com.ultracards.server.service.games.treseta;

import com.ultracards.cards.ItalianCard;
import com.ultracards.games.treseta.TresetaGameConfig;
import com.ultracards.games.treseta.TresetaCard;
import com.ultracards.gateway.dto.games.games.GameCardDTO;
import com.ultracards.server.entity.UserEntity;
import com.ultracards.server.entity.games.treseta.TresetaGameEntity;
import com.ultracards.server.entity.games.treseta.TresetaPlayerEntity;
import com.ultracards.server.enums.games.GameType;
import com.ultracards.server.service.games.GameManager;
import com.ultracards.server.service.games.GameRecordingService;
import com.ultracards.server.service.games.UserGamesStatsService;
import com.ultracards.server.service.games.briskula.GameEventPublisher;
import com.ultracards.server.service.lobby.LobbyManager;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

import static com.ultracards.gateway.dto.games.games.GameEventDTO.GameEventTypeDTO.RESULTED;
import static com.ultracards.gateway.dto.games.games.GameEventDTO.GameEventTypeDTO.STARTED;
import static com.ultracards.gateway.dto.games.games.GameEventDTO.GameEventTypeDTO.UPDATED;

@Service
public class TresetaGameService {
    private final GameManager gameManager;
    private final GameEventPublisher eventPublisher;
    private final LobbyManager lobbyManager;
    private final UserGamesStatsService userGamesStatsService;
    private final UserTresetaStatsService userTresetaStatsService;
    private final GameRecordingService gameRecordingService;
    private final TaskScheduler taskScheduler;
    private final Function<com.ultracards.server.entity.lobby.LobbyEntity, Boolean> openLobby;

    @Value("${app.treseta-move.timer.duration-seconds}")
    private int timerDuration;

    public TresetaGameService(GameManager gameManager, GameEventPublisher eventPublisher, LobbyManager lobbyManager,
                              UserGamesStatsService userGamesStatsService,
                              UserTresetaStatsService userTresetaStatsService,
                              GameRecordingService gameRecordingService,
                              @Qualifier("timer") TaskScheduler taskScheduler,
                              @Qualifier("openLobby") @Lazy Function<com.ultracards.server.entity.lobby.LobbyEntity, Boolean> openLobby) {
        this.gameManager = gameManager;
        this.eventPublisher = eventPublisher;
        this.lobbyManager = lobbyManager;
        this.userGamesStatsService = userGamesStatsService;
        this.userTresetaStatsService = userTresetaStatsService;
        this.gameRecordingService = gameRecordingService;
        this.taskScheduler = taskScheduler;
        this.openLobby = openLobby;
    }

    public void onGameStarted(TresetaGameEntity game) {
        game.setTurnDurationSeconds(timerDuration);
        setTimer(game);
        eventPublisher.publish(game, STARTED);
    }

    public void playCard(UserEntity user, GameCardDTO dto, TresetaGameEntity game) {
        synchronized (game) {
            var card = dto.toCard();
            if (!(card instanceof ItalianCard<?>)) return;
            var handsBeforePlay = opponentHands(game);
            var oldField = game.getGame().getPlayingField();
            if (!game.playCard(user, card)) return;
            var newField = game.getGame().getPlayingField();
            if (newField == null || newField.getPlayedCards().isEmpty()) {
                game.getGame().setPlayingField(oldField);
                eventPublisher.publish(game, UPDATED);
                game.getGame().setPlayingField(newField);
            }
            if (!game.isActive()) {
                finish(game);
                return;
            }
            if (game.getPersistedGameConfig().equals(TresetaGameConfig.TWO_PLAYERS))
                eventPublisher.publishOpponentDrawnCards(game, handsBeforePlay);
            setTimer(game);
            eventPublisher.publish(game, UPDATED);
        }
    }

    private HashMap<Long, List<TresetaCard>> opponentHands(TresetaGameEntity game) {
        var hands = new HashMap<Long, List<TresetaCard>>();
        for (var raw : game.getGame().getPlayers()) {
            var player = (TresetaPlayerEntity) raw;
            hands.put(player.getUser().getId(), List.copyOf(player.getHand().getCards()));
        }
        return hands;
    }

    private void setTimer(TresetaGameEntity game) {
        game.setTurnEndTime(Instant.now().plusSeconds(timerDuration));
        var turn = game.getTurnNumber();
        taskScheduler.schedule(() -> {
            if (!game.isActive() || !game.getTurnNumber().equals(turn)) return;
            var player = game.getCurrentPlayer();
            if (player != null && !player.getHand().getCards().isEmpty()) {
                var cards = player.getHand().getCards();
                var card = cards.getFirst();
                var field = game.getGame().getPlayingField();
                if (field != null && !field.getPlayedCards().isEmpty()) {
                    var suit = field.getPlayedCards().getFirst().getSuit();
                    for (var candidate : cards)
                        if (candidate.getSuit().equals(suit)) {
                            card = candidate;
                            break;
                        }
                }
                playCard(player.getUser(), GameCardDTO.createCardDTO(card), game);
            }
        }, game.getTurnEndTime());
    }

    private void finish(TresetaGameEntity game) {
        var winners = new HashSet<>(game.getGame().determineGameWinners());
        var gameConfig = game.getPersistedGameConfig();
        var winnerUsers = new HashSet<UserEntity>();
        for (var raw : game.getGame().getPlayers()) {
            var player = (TresetaPlayerEntity) raw;
            var won = winners.contains(player);
            if (won) winnerUsers.add(player.getUser());
            userGamesStatsService.addGame(player.getUser(), GameType.TRESETA, won);
            userTresetaStatsService.addTresetaGame(player.getUser(), gameConfig, won);
        }
        updateTresetaRelationshipStats(game.getPlayers(), winnerUsers, gameConfig);
        gameRecordingService.finish(game);
        eventPublisher.publish(game, RESULTED);
        gameManager.deleteGame(game);
        openLobby.apply(lobbyManager.getLobby(game.getLobbyId()));
    }

    private void updateTresetaRelationshipStats(List<UserEntity> players, Set<UserEntity> winnerUsers,
                                                TresetaGameConfig gameConfig) {
        var loserUsers = new ArrayList<UserEntity>();
        for (var player : players)
            if (!winnerUsers.contains(player)) loserUsers.add(player);

        for (var winner : winnerUsers)
            for (var loser : loserUsers)
                if (!winner.equals(loser)) {
                    userTresetaStatsService.addTresetaGameAgainstUser(winner, gameConfig, loser, true);
                    userTresetaStatsService.addTresetaGameAgainstUser(loser, gameConfig, winner, false);
                }

        if (gameConfig.areTeamsEnabled()) {
            addTeammateGames(new ArrayList<>(winnerUsers), gameConfig, true);
            addTeammateGames(loserUsers, gameConfig, false);
        }
    }

    private void addTeammateGames(List<UserEntity> team, TresetaGameConfig gameConfig, boolean won) {
        for (int i = 0; i < team.size(); i++)
            for (int j = 0; j < team.size(); j++)
                if (i != j)
                    userTresetaStatsService.addTresetaGameWithTeammate(team.get(i), gameConfig, team.get(j), won);
    }
}
