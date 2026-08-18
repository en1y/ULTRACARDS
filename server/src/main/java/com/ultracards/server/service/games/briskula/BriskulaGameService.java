package com.ultracards.server.service.games.briskula;

import com.ultracards.cards.ItalianCard;
import com.ultracards.games.briskula.BriskulaGameConfig;
import com.ultracards.gateway.dto.games.games.GameCardDTO;
import com.ultracards.server.entity.UserEntity;
import com.ultracards.server.entity.games.GameEntity;
import com.ultracards.server.entity.games.briskula.BriskulaGameEntity;
import com.ultracards.server.entity.games.briskula.BriskulaPlayerEntity;
import com.ultracards.server.entity.lobby.LobbyEntity;
import com.ultracards.server.enums.games.GameType;
import com.ultracards.server.service.games.GameManager;
import com.ultracards.server.service.games.GameRecordingService;
import com.ultracards.server.service.games.UserGamesStatsService;
import com.ultracards.server.service.lobby.LobbyManager;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import com.ultracards.server.service.points.PointsService;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;

import static com.ultracards.gateway.dto.games.games.GameEventDTO.GameEventTypeDTO.*;

@Slf4j
@Service
public class BriskulaGameService {
    private final GameManager gameManager;
    private final GameEventPublisher eventPublisher;
    private final BriskulaEventPublisher briskulaEventPublisher;
    private final LobbyManager lobbyManager;
    private final UserBriskulaStatsService userBriskulaStatsService;
    private final UserGamesStatsService userGamesStatsService;
    private final GameRecordingService gameRecordingService;
    private final TaskScheduler taskScheduler;
    private final Function<LobbyEntity, Boolean> openLobby;
    private final TransactionTemplate transactionTemplate;
    private final PointsService pointsService;
    private final Map<BriskulaGameConfig, BiFunction<UserEntity, BriskulaGameEntity, Void>> onCardPlayedByConfig;

    @Value("${app.briskula-move.timer.duration-seconds}")
    private int briskulaTimerDuration;

    public BriskulaGameService(
            GameManager gameManager,
            GameEventPublisher eventPublisher,
            BriskulaEventPublisher briskulaEventPublisher,
            LobbyManager lobbyManager,
            UserBriskulaStatsService userBriskulaStatsService,
            UserGamesStatsService userGamesStatsService,
            GameRecordingService gameRecordingService,
            @Qualifier("timer") TaskScheduler taskScheduler,
            TransactionTemplate transactionTemplate,
            PointsService pointsService,
            @Qualifier("openLobby") @Lazy Function<LobbyEntity, Boolean> openLobby) {
        this.gameManager = gameManager;
        this.eventPublisher = eventPublisher;
        this.briskulaEventPublisher = briskulaEventPublisher;
        this.lobbyManager = lobbyManager;
        this.userBriskulaStatsService = userBriskulaStatsService;
        this.userGamesStatsService = userGamesStatsService;
        this.gameRecordingService = gameRecordingService;
        this.taskScheduler = taskScheduler;
        this.transactionTemplate = transactionTemplate;
        this.pointsService = pointsService;
        this.openLobby = openLobby;
        this.onCardPlayedByConfig = createOnCardPlayedByConfig();
    }

    private Map<BriskulaGameConfig, BiFunction<UserEntity, BriskulaGameEntity, Void>> createOnCardPlayedByConfig() {
        var map = new EnumMap<BriskulaGameConfig, BiFunction<UserEntity, BriskulaGameEntity, Void>>(BriskulaGameConfig.class);
        map.put(BriskulaGameConfig.TWO_PLAYERS, (user, game) -> null);
        map.put(BriskulaGameConfig.TWO_PLAYERS_FOUR_CARDS_IN_HAND_EACH, (user, game) -> null);
        map.put(BriskulaGameConfig.THREE_PLAYERS, (user, game) -> null);
        map.put(BriskulaGameConfig.FOUR_PLAYERS_NO_TEAMS, (user, game) -> null);
        map.put(BriskulaGameConfig.FOUR_PLAYERS_WITH_TEAMS, (user, game) -> {
            var briskulaGame = game.getGame();
            if (briskulaGame.getDeck().getSize() == 0 && !game.isHaveTeammateCardBeenDisplayed()) {
                briskulaEventPublisher.publishTeammateHands(game);
                game.setHaveTeammateCardBeenDisplayed(true);
            }
            return null;
        });
        return map;
    }

    public void onGameStarted(BriskulaGameEntity game) {
        game.setTurnDurationSeconds(briskulaTimerDuration);
        setTimer(game);
        eventPublisher.publish(game, STARTED);
    }

    public void setTimer(BriskulaGameEntity game) {
        game.setTurnEndTime(Instant.now().plusSeconds(briskulaTimerDuration));
        var prevTurnNum = game.getTurnNumber();
        taskScheduler.schedule(() -> {
            if (game.getTurnNumber().equals(prevTurnNum)) {
                var player = game.getCurrentPlayer();
                var card = GameCardDTO.createCardDTO(player.getHand().getCards().getFirst());
                if (card != null) playCard(player.getUser(), card, game);
            }
        }, game.getTurnEndTime());
    }

    public void playCard(UserEntity user, GameCardDTO cardDTO, BriskulaGameEntity game) {
        synchronized (game) {
            var genericCard = cardDTO.toCard();
            if (!(genericCard instanceof ItalianCard<?>)) return;

            var briskulaGame = game.getGame();
            var oldField = briskulaGame.getPlayingField();
            if (!game.playCard(user, genericCard)) return;
            var newField = briskulaGame.getPlayingField();
            if (newField == null || newField.getPlayedCards().isEmpty()) {
                briskulaGame.setPlayingField(oldField);
                eventPublisher.publish(game, UPDATED);
                briskulaGame.setPlayingField(newField);
            }

            if (!game.isActive()) {
                handleEndGame(game);
                return;
            }
            setTimer(game);
            eventPublisher.publish(game, UPDATED);

            onCardPlayedByConfig.get(game.getPersistedGameConfig()).apply(user, game);
        }
    }

    private void handleEndGame(BriskulaGameEntity game) {
        try {
            if (!game.isFinalizationPersisted()) {
                transactionTemplate.executeWithoutResult(status -> persistFinalization(game));
                gameRecordingService.release(game);
                game.setFinalizationPersisted(true);
            }
            if (!game.isResultPublished()) {
                eventPublisher.publish(game, RESULTED);
                game.setResultPublished(true);
            }
            if (!game.isLobbyReopened()) {
                var lobby = lobbyManager.getLobby(game.getLobbyId());
                if (lobby != null) openLobby.apply(lobby);
                game.setLobbyReopened(true);
            }
            gameManager.deleteGame(game);
        } catch (RuntimeException ex) {
            log.error("Briskula finalization failed for game {}; retrying", game.getId(), ex);
            scheduleFinishRetry(game);
        }
    }

    private void persistFinalization(BriskulaGameEntity game) {
        var winners = game.getGame().determineGameWinners();
        var gameConfig = game.getPersistedGameConfig();
        var winnerUsers = new HashSet<UserEntity>();
        winners.forEach(p -> winnerUsers.add(((BriskulaPlayerEntity) p).getUser()));
        game.getGame().getPlayers().forEach(p -> {
            var player = (BriskulaPlayerEntity) p;
            var won = winners.contains(p);
            userGamesStatsService.addGame(player.getUser(), GameType.BRISKULA, won);
            userBriskulaStatsService.addBriskulaGame(player.getUser(), gameConfig, won);
        });
        updateBriskulaRelationshipStats(game.getPlayers(), winnerUsers, gameConfig);
        gameRecordingService.finish(game);
        var winnerIds = new HashSet<Long>();
        for (var winner : winnerUsers) winnerIds.add(winner.getId());
        pointsService.completeGame(game.getId(), game.getPlayers(), winnerIds, GameType.BRISKULA, gameConfig.name());
    }

    private void scheduleFinishRetry(BriskulaGameEntity game) {
        if (game.isFinishRetryScheduled()) return;
        game.setFinishRetryScheduled(true);
        var gameId = game.getId();
        taskScheduler.schedule(() -> {
            if (!(gameManager.getGame(gameId) instanceof BriskulaGameEntity current)) return;
            synchronized (current) {
                current.setFinishRetryScheduled(false);
                if (!current.isActive()) handleEndGame(current);
            }
        }, Instant.now().plusSeconds(5));
    }

    private void updateBriskulaRelationshipStats(List<UserEntity> players, Set<UserEntity> winnerUsers,
                                                  BriskulaGameConfig gameConfig) {
        var loserUsers = new ArrayList<UserEntity>();
        for (var player : players)
            if (!winnerUsers.contains(player)) loserUsers.add(player);

        for (var winner : winnerUsers)
            for (var loser : loserUsers)
                if (!winner.equals(loser)) {
                    userBriskulaStatsService.addBriskulaGameAgainstUser(winner, gameConfig, loser, true);
                    userBriskulaStatsService.addBriskulaGameAgainstUser(loser, gameConfig, winner, false);
                }

        if (gameConfig.areTeamsEnabled() && winnerUsers.size() > 1) {
            var winnersList = new ArrayList<>(winnerUsers);
            for (int i = 0; i < winnersList.size(); i++)
                for (int j = 0; j < winnersList.size(); j++)
                    if (i != j)
                        userBriskulaStatsService.addBriskulaGameWithTeammate(winnersList.get(i), gameConfig, winnersList.get(j), true);
        }

        if (gameConfig.areTeamsEnabled())
            updateBriskulaTeammatePlayedStats(players, winnerUsers, gameConfig);
    }

    private void updateBriskulaTeammatePlayedStats(List<UserEntity> players, Set<UserEntity> winnerUsers,
                                                    BriskulaGameConfig gameConfig) {
        var loserUsers = new ArrayList<UserEntity>();
        for (var player : players)
            if (!winnerUsers.contains(player)) loserUsers.add(player);

        if (loserUsers.size() > 1)
            for (int i = 0; i < loserUsers.size(); i++)
                for (int j = 0; j < loserUsers.size(); j++)
                    if (i != j)
                        userBriskulaStatsService.addBriskulaGameWithTeammate(loserUsers.get(i), gameConfig, loserUsers.get(j), false);
    }
}
