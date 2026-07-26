package com.ultracards.server.service.games.durak;

import com.ultracards.games.durak.DurakErrorCode;
import com.ultracards.games.durak.DurakRuleException;
import com.ultracards.gateway.dto.games.games.durak.DurakActionRequestDTO;
import com.ultracards.server.entity.UserEntity;
import com.ultracards.server.entity.games.durak.DurakGameEntity;
import com.ultracards.server.entity.games.durak.DurakPlayerEntity;
import com.ultracards.server.entity.lobby.LobbyEntity;
import com.ultracards.server.enums.games.GameType;
import com.ultracards.server.service.games.GameManager;
import com.ultracards.server.service.games.GameRecordingService;
import com.ultracards.server.service.games.UserGamesStatsService;
import com.ultracards.server.service.games.briskula.GameEventPublisher;
import com.ultracards.server.service.lobby.LobbyManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.function.Function;

import static com.ultracards.gateway.dto.games.games.GameEventDTO.GameEventTypeDTO.RESULTED;
import static com.ultracards.gateway.dto.games.games.GameEventDTO.GameEventTypeDTO.STARTED;
import static com.ultracards.gateway.dto.games.games.GameEventDTO.GameEventTypeDTO.UPDATED;

/**
 * Orchestrates live Durak games: authentication of the actor, locking, revision-aware timers,
 * recording, statistics and publication. Card rules stay in the game-logic module.
 */
@Slf4j
@Service
public class DurakGameService {
    private final GameManager gameManager;
    private final GameEventPublisher eventPublisher;
    private final LobbyManager lobbyManager;
    private final UserGamesStatsService userGamesStatsService;
    private final UserDurakStatsService userDurakStatsService;
    private final GameRecordingService gameRecordingService;
    private final TaskScheduler taskScheduler;
    private final TransactionTemplate transactionTemplate;
    private final Function<LobbyEntity, Boolean> openLobby;

    @Value("${app.durak-action.timer.duration-seconds}")
    private int timerDuration;

    public DurakGameService(GameManager gameManager, GameEventPublisher eventPublisher, LobbyManager lobbyManager,
                           UserGamesStatsService userGamesStatsService,
                           UserDurakStatsService userDurakStatsService,
                           GameRecordingService gameRecordingService,
                           @Qualifier("timer") TaskScheduler taskScheduler,
                           TransactionTemplate transactionTemplate,
                           @Qualifier("openLobby") @Lazy Function<LobbyEntity, Boolean> openLobby) {
        this.gameManager = gameManager;
        this.eventPublisher = eventPublisher;
        this.lobbyManager = lobbyManager;
        this.userGamesStatsService = userGamesStatsService;
        this.userDurakStatsService = userDurakStatsService;
        this.gameRecordingService = gameRecordingService;
        this.taskScheduler = taskScheduler;
        this.transactionTemplate = transactionTemplate;
        this.openLobby = openLobby;
    }

    public void onGameStarted(DurakGameEntity game) {
        synchronized (game) {
            scheduleTimeout(game);
            eventPublisher.publish(game, STARTED);
        }
    }

    /**
     * Applies one authenticated action. Everything from the revision check to publication happens
     * under the game lock, so two simultaneous actions cannot both mutate the table.
     */
    public void action(UserEntity user, DurakActionRequestDTO request, DurakGameEntity game) {
        synchronized (game) {
            try {
                game.apply(user, request);
            } catch (DurakRuleException ex) {
                eventPublisher.publishDurakError(game, user.getId(), ex.getCode(), ex.getMessage());
                return;
            }
            if (!game.isActive()) {
                finish(game);
                return;
            }
            scheduleTimeout(game);
            eventPublisher.publish(game, UPDATED);
        }
    }

    /**
     * A timeout task carries the revision it was scheduled for and exits when the game moved on,
     * so an old timer can never act against a newer state.
     */
    private void scheduleTimeout(DurakGameEntity game) {
        game.setTurnDurationSeconds(timerDuration);
        game.setTurnEndTime(Instant.now().plusSeconds(timerDuration));
        var gameId = game.getId();
        var expectedRevision = game.getStateRevision();
        taskScheduler.schedule(() -> onTimeout(gameId, expectedRevision), game.getTurnEndTime());
    }

    private void onTimeout(java.util.UUID gameId, long expectedRevision) {
        if (!(gameManager.getGame(gameId) instanceof DurakGameEntity game)) return;
        synchronized (game) {
            if (!game.isActive() || game.getStateRevision() != expectedRevision) return;
            var actor = game.getActionPlayer();
            if (actor == null) return;
            try {
                game.getGame().apply(actor, game.getGame().timeoutAction());
                game.setStateRevision(game.getStateRevision() + 1);
                game.setTurnNumber(game.getTurnNumber() + 1);
            } catch (RuntimeException ex) {
                log.warn("Durak timeout action failed for game {}: {}", gameId, ex.getMessage());
                return;
            }
            if (!game.isActive()) {
                finish(game);
                return;
            }
            scheduleTimeout(game);
            eventPublisher.publish(game, UPDATED);
        }
    }

    /** Persists the result atomically, then completes the non-transactional publication steps. */
    private void finish(DurakGameEntity game) {
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
            log.error("Durak finalization failed for game {}; retrying", game.getId(), ex);
            scheduleFinishRetry(game);
        }
    }

    private void persistFinalization(DurakGameEntity game) {
        var logic = game.getGame();
        var modeKey = game.getPersistedGameConfig().modeKey();
        var winners = new HashSet<>(logic.determineGameWinners());
        var loser = logic.getLoser() == null ? null : ((DurakPlayerEntity) logic.getLoser()).getUser();

        var users = new ArrayList<UserEntity>();
        for (var raw : logic.getPlayers()) {
            var player = (DurakPlayerEntity) raw;
            var user = player.getUser();
            users.add(user);
            var won = winners.contains(player);
            userGamesStatsService.addGame(user, GameType.DURAK, won);
            userDurakStatsService.addDurakGame(user, modeKey, won);
            if (logic.isDraw()) {
                userDurakStatsService.addDraw(user);
            } else if (!won) {
                userDurakStatsService.addTimesDurak(user);
            }
        }
        updateMatchupStats(users, modeKey, loser);

        gameRecordingService.finish(game);
    }

    private void scheduleFinishRetry(DurakGameEntity game) {
        if (game.isFinishRetryScheduled()) return;
        game.setFinishRetryScheduled(true);
        var gameId = game.getId();
        taskScheduler.schedule(() -> retryFinish(gameId), Instant.now().plusSeconds(5));
    }

    private void retryFinish(java.util.UUID gameId) {
        if (!(gameManager.getGame(gameId) instanceof DurakGameEntity game)) return;
        synchronized (game) {
            game.setFinishRetryScheduled(false);
            if (!game.isActive()) finish(game);
        }
    }

    /** Only the durak is "beaten": two non-losers never count as having beaten each other. */
    private void updateMatchupStats(java.util.List<UserEntity> users, String modeKey, UserEntity loser) {
        for (var subject : users) {
            for (var other : users) {
                if (subject.equals(other)) continue;
                var beatThem = loser != null && other.equals(loser) && !subject.equals(loser);
                userDurakStatsService.addGameAgainstUser(subject, modeKey, other, beatThem);
            }
        }
    }

    /** Generic {@code /game/play} messages have no meaning in Durak. */
    public void rejectGenericPlay(UserEntity user, DurakGameEntity game) {
        eventPublisher.publishDurakError(game, user.getId(), DurakErrorCode.DURAK_INVALID_ACTION_FOR_PHASE,
                "Durak actions must be sent to /game/durak/action.");
    }
}
