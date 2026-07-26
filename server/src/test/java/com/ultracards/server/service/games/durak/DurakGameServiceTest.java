package com.ultracards.server.service.games.durak;

import com.ultracards.games.durak.*;
import com.ultracards.gateway.dto.games.games.GameCardDTO;
import com.ultracards.gateway.dto.games.games.GameEventDTO;
import com.ultracards.gateway.dto.games.games.durak.DurakActionRequestDTO;
import com.ultracards.gateway.dto.games.games.durak.DurakActionTypeDTO;
import com.ultracards.gateway.dto.games.games.durak.DurakGameConfigDTO;
import com.ultracards.gateway.dto.games.games.durak.DurakThrowInPolicyDTO;
import com.ultracards.server.entity.UserEntity;
import com.ultracards.server.entity.games.durak.DurakGameEntity;
import com.ultracards.server.entity.games.durak.DurakPlayerEntity;
import com.ultracards.server.entity.lobby.DurakLobbyGameConfig;
import com.ultracards.server.entity.lobby.LobbyEntity;
import com.ultracards.server.enums.games.GameType;
import com.ultracards.server.service.games.GameManager;
import com.ultracards.server.service.games.GameRecordingService;
import com.ultracards.server.service.games.UserGamesStatsService;
import com.ultracards.server.service.games.briskula.GameEventPublisher;
import com.ultracards.server.service.lobby.LobbyManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class DurakGameServiceTest {

    private final GameManager gameManager = mock(GameManager.class);
    private final GameEventPublisher eventPublisher = mock(GameEventPublisher.class);
    private final LobbyManager lobbyManager = mock(LobbyManager.class);
    private final UserGamesStatsService userGamesStatsService = mock(UserGamesStatsService.class);
    private final UserDurakStatsService userDurakStatsService = mock(UserDurakStatsService.class);
    private final GameRecordingService gameRecordingService = mock(GameRecordingService.class);
    private final TaskScheduler taskScheduler = mock(TaskScheduler.class);
    private final TransactionTemplate transactionTemplate = mock(TransactionTemplate.class);
    @SuppressWarnings("unchecked")
    private final Function<LobbyEntity, Boolean> openLobby = mock(Function.class);

    private DurakGameService service;

    @BeforeEach
    void setUp() {
        service = new DurakGameService(gameManager, eventPublisher, lobbyManager, userGamesStatsService,
                userDurakStatsService, gameRecordingService, taskScheduler, transactionTemplate, openLobby);
        doAnswer(invocation -> {
            java.util.function.Consumer<TransactionStatus> action = invocation.getArgument(0);
            action.accept(mock(TransactionStatus.class));
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());
        ReflectionTestUtils.setField(service, "timerDuration", 30);
    }

    private static UserEntity user(long id, String name) {
        var user = new UserEntity(name + "@example.com", name);
        user.setId(id);
        return user;
    }

    private static DurakGameEntity game(int players, boolean passing) {
        var users = new ArrayList<UserEntity>();
        for (long i = 1; i <= players; i++) users.add(user(i, "P" + i));
        var dto = new DurakGameConfigDTO(players, 36, false, DurakThrowInPolicyDTO.EVERYONE, passing, null);
        var game = new DurakGameEntity(UUID.randomUUID(), "table", users.getFirst(),
                new DurakLobbyGameConfig(dto, users), users);
        game.getGame().start();
        return game;
    }

    @Test
    void publishesStartedAndSchedulesTheFirstTimeout() {
        var game = game(2, false);
        service.onGameStarted(game);

        verify(eventPublisher).publish(game, GameEventDTO.GameEventTypeDTO.STARTED);
        verify(taskScheduler).schedule(any(Runnable.class), any(java.time.Instant.class));
        assertThat(game.getTurnEndTime()).isNotNull();
        assertThat(game.getTurnDurationSeconds()).isEqualTo(30);
    }

    @Test
    void aStaleRevisionIsReportedToTheActorAndChangesNothing() {
        var game = game(2, false);
        var actor = game.getActionPlayer();
        var card = actor.getHand().getCards().getFirst();

        service.action(actor.getUser(), new DurakActionRequestDTO(DurakActionTypeDTO.ATTACK,
                GameCardDTO.createCardDTO(card), null, 99L), game);

        verify(eventPublisher).publishDurakError(eq(game), eq(actor.getUser().getId()),
                eq(DurakErrorCode.DURAK_STALE_REVISION), anyString());
        verify(eventPublisher, never()).publish(any(), eq(GameEventDTO.GameEventTypeDTO.UPDATED));
        assertThat(game.getStateRevision()).isZero();
        assertThat(actor.handSize()).isEqualTo(6);
    }

    @Test
    void anIllegalActionIsReportedWithoutAdvancingTheRevision() {
        var game = game(2, false);
        var actor = game.getActionPlayer();

        service.action(actor.getUser(), new DurakActionRequestDTO(DurakActionTypeDTO.TAKE, null, null, 0L), game);

        verify(eventPublisher).publishDurakError(eq(game), eq(actor.getUser().getId()),
                eq(DurakErrorCode.DURAK_INVALID_ACTION_FOR_PHASE), anyString());
        assertThat(game.getStateRevision()).isZero();
    }

    @Test
    void aGenericPlayCardMessageIsRefusedWithAUserVisibleError() {
        var game = game(2, false);
        var actor = game.getActionPlayer();

        service.rejectGenericPlay(actor.getUser(), game);

        verify(eventPublisher).publishDurakError(eq(game), eq(actor.getUser().getId()),
                eq(DurakErrorCode.DURAK_INVALID_ACTION_FOR_PHASE), anyString());
    }

    @Test
    void aCompleteGameFinishesExactlyOnceAndReportsOneLoser() {
        var game = game(3, true);
        var lobby = mock(LobbyEntity.class);
        when(lobbyManager.getLobby(game.getLobbyId())).thenReturn(lobby);

        playOut(game);

        assertThat(game.isActive()).isFalse();
        verify(gameRecordingService, times(1)).finish(game);
        verify(gameRecordingService, times(1)).release(game);
        verify(eventPublisher, times(1)).publish(game, GameEventDTO.GameEventTypeDTO.RESULTED);
        verify(gameManager, times(1)).deleteGame(game);
        verify(openLobby, times(1)).apply(lobby);

        var logic = game.getGame();
        var modeKey = game.getPersistedGameConfig().modeKey();
        for (var raw : logic.getPlayers()) {
            var player = (DurakPlayerEntity) raw;
            var won = logic.determineGameWinners().contains(player);
            verify(userGamesStatsService).addGame(player.getUser(), GameType.DURAK, won);
            verify(userDurakStatsService).addDurakGame(player.getUser(), modeKey, won);
        }
        if (logic.isDraw()) {
            assertThat(logic.getLoser()).isNull();
        } else {
            var loser = ((DurakPlayerEntity) logic.getLoser()).getUser();
            verify(userDurakStatsService).addTimesDurak(loser);
            // Only the durak is beaten: no winner is credited with beating another winner.
            for (var raw : logic.getPlayers()) {
                var other = ((DurakPlayerEntity) raw).getUser();
                if (other.equals(loser)) continue;
                verify(userDurakStatsService).addGameAgainstUser(other, modeKey, loser, true);
                verify(userDurakStatsService).addGameAgainstUser(loser, modeKey, other, false);
            }
        }
        // The result event never carries cards, and no further UPDATED events follow it.
        var order = inOrder(gameRecordingService, eventPublisher, gameManager);
        order.verify(gameRecordingService).finish(game);
        order.verify(eventPublisher).publish(game, GameEventDTO.GameEventTypeDTO.RESULTED);
        order.verify(gameManager).deleteGame(game);
    }

    @Test
    void aFailedFinalizationIsRetriedWithoutPublishingAPartialResult() {
        var game = game(2, false);
        var lobby = mock(LobbyEntity.class);
        doReturn(game).when(gameManager).getGame(game.getId());
        when(lobbyManager.getLobby(game.getLobbyId())).thenReturn(lobby);
        doThrow(new IllegalStateException("database unavailable"))
                .doNothing()
                .when(gameRecordingService).finish(game);

        playOut(game);

        verify(eventPublisher, never()).publish(game, GameEventDTO.GameEventTypeDTO.RESULTED);
        verify(gameManager, never()).deleteGame(game);
        assertThat(game.isFinalizationPersisted()).isFalse();

        capturedTimeout().run();

        verify(transactionTemplate, times(2)).executeWithoutResult(any());
        verify(gameRecordingService, times(2)).finish(game);
        verify(gameRecordingService).release(game);
        verify(eventPublisher).publish(game, GameEventDTO.GameEventTypeDTO.RESULTED);
        verify(gameManager).deleteGame(game);
        verify(openLobby).apply(lobby);
        assertThat(game.isFinalizationPersisted()).isTrue();
    }

    @Test
    void aPublicationRetryDoesNotWriteStatisticsOrHistoryTwice() {
        var game = game(2, false);
        doReturn(game).when(gameManager).getGame(game.getId());
        doThrow(new IllegalStateException("broker unavailable"))
                .doNothing()
                .when(eventPublisher).publish(game, GameEventDTO.GameEventTypeDTO.RESULTED);

        playOut(game);

        assertThat(game.isFinalizationPersisted()).isTrue();
        verify(gameRecordingService).finish(game);
        verify(gameRecordingService).release(game);
        verify(gameManager, never()).deleteGame(game);

        capturedTimeout().run();

        verify(transactionTemplate).executeWithoutResult(any());
        verify(gameRecordingService).finish(game);
        verify(eventPublisher, times(2)).publish(game, GameEventDTO.GameEventTypeDTO.RESULTED);
        verify(gameManager).deleteGame(game);
    }

    @Test
    void aTimeoutScheduledForAnOlderRevisionDoesNothing() {
        var game = game(2, false);
        doReturn(game).when(gameManager).getGame(game.getId());
        service.onGameStarted(game);
        var staleTimeout = capturedTimeout();

        // A real action happens first, so the captured task is now one revision behind.
        var actor = game.getActionPlayer();
        service.action(actor.getUser(), new DurakActionRequestDTO(DurakActionTypeDTO.ATTACK,
                GameCardDTO.createCardDTO(actor.getHand().getCards().getFirst()), null, 0L), game);
        var revisionAfterAction = game.getStateRevision();

        staleTimeout.run();

        assertThat(game.getStateRevision()).isEqualTo(revisionAfterAction);
    }

    @Test
    void aCurrentTimeoutAppliesThePhaseFallbackAction() {
        var game = game(2, false);
        doReturn(game).when(gameManager).getGame(game.getId());
        service.onGameStarted(game);

        capturedTimeout().run();

        assertThat(game.getStateRevision()).isEqualTo(1L);
        assertThat(game.getGame().getPlayingField().getAttackSlots()).hasSize(1);
    }

    private Runnable capturedTimeout() {
        var captor = org.mockito.ArgumentCaptor.forClass(Runnable.class);
        verify(taskScheduler, atLeastOnce()).schedule(captor.capture(), any(java.time.Instant.class));
        return captor.getAllValues().getLast();
    }

    /** Drives a whole game through the service, always sending the current revision. */
    private void playOut(DurakGameEntity game) {
        var guard = 0;
        while (game.isActive()) {
            if (++guard > 20_000) throw new IllegalStateException("game did not terminate");
            var logic = game.getGame();
            var field = logic.getPlayingField();
            var actor = (DurakPlayerEntity) field.getActionPlayer();
            var action = next(logic, field, actor);
            service.action(actor.getUser(), new DurakActionRequestDTO(
                    DurakActionTypeDTO.valueOf(action.type().name()),
                    action.card() == null ? null : GameCardDTO.createCardDTO(action.card()),
                    action.targetSlotId(), game.getStateRevision()), game);
        }
    }

    private DurakAction next(DurakGame game, DurakPlayingField field, DurakPlayer actor) {
        switch (field.getPhase()) {
            case WAITING_FOR_ATTACK -> {
                return game.timeoutAction();
            }
            case WAITING_FOR_DEFENSE -> {
                var target = field.uncoveredSlots().getFirst();
                for (var card : List.copyOf(actor.getHand().getCards())) {
                    if (game.canBeat(target.attackCard(), card)) return DurakAction.defend(card, target.slotId());
                }
                for (var card : List.copyOf(actor.getHand().getCards())) {
                    if (game.canPass(actor, card)) return DurakAction.pass(card);
                }
                return DurakAction.take();
            }
            default -> {
                for (var card : List.copyOf(actor.getHand().getCards())) {
                    if (game.canThrowIn(card)) return DurakAction.throwIn(card);
                }
                return DurakAction.done();
            }
        }
    }
}
