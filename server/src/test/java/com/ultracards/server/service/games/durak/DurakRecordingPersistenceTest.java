package com.ultracards.server.service.games.durak;

import com.ultracards.games.durak.*;
import com.ultracards.recorder.DurakGameRecorder;
import com.ultracards.recorder.RecordedPlay;
import com.ultracards.recorder.RecordedPlayer;
import com.ultracards.server.entity.UserEntity;
import com.ultracards.server.repositories.games.DurakGameRepository;
import com.ultracards.server.repositories.games.TresetaGameRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Saves a real recorded Durak game, clears the persistence context and reloads it, so the shared
 * recorder tables, the Durak subtype, the finish-order collection and the new play columns are all
 * exercised against the real migration chain.
 */
@SpringBootTest(properties = {
        "spring.main.web-application-type=none",
        "app.database.startup-check.enabled=false",
        "app.mail.startup-check.enabled=false"
})
@Transactional
class DurakRecordingPersistenceTest {
    @Autowired
    private DurakGameRepository repository;
    @Autowired
    private TresetaGameRepository tresetaRepository;
    @Autowired
    private DurakGameHistoryService historyService;
    @Autowired
    private EntityManager entityManager;

    private static final DurakGameConfig CONFIG =
            new DurakGameConfig(3, 54, true, DurakThrowInPolicy.EVERYONE, true);

    @Test
    void persistsAndReloadsACompleteDurakGame() {
        var players = new ArrayList<DurakPlayer>();
        for (int seat = 0; seat < CONFIG.numberOfPlayers(); seat++) {
            players.add(new DurakPlayer("P" + seat, seat));
        }
        var game = new DurakGame(players, CONFIG, new Random(7));
        var recorder = new DurakGameRecorder(UUID.randomUUID(), UUID.randomUUID(), "persistence test", 0L, CONFIG,
                player -> new RecordedPlayer((long) (((DurakPlayer) player).getSeat() + 1), player.getName()));
        recorder.attach(game);
        game.start();
        playOut(game);

        var recording = recorder.recording();
        var finishOrder = new ArrayList<Long>();
        for (var player : game.getFinishOrder()) finishOrder.add((long) player.getSeat() + 1);
        recording.result(game.getTrumpSuit().name(), game.getTrumpIndicator().code(),
                game.getLoser() == null ? null : (long) game.getLoser().getSeat() + 1,
                game.isDraw(), finishOrder);

        repository.saveAndFlush(recording);
        entityManager.createNativeQuery("""
                SET CONSTRAINTS fk_recorded_durak_games_loser_player,
                                fk_recorded_durak_finish_order_player IMMEDIATE
                """).executeUpdate();
        entityManager.clear();

        var persisted = repository.findById(recording.id()).orElseThrow();
        assertThat(persisted.modeKey()).isEqualTo(CONFIG.modeKey());
        assertThat(persisted.deckSize()).isEqualTo(54);
        assertThat(persisted.jokersEnabled()).isTrue();
        assertThat(persisted.passingEnabled()).isTrue();
        assertThat(persisted.throwInPolicy()).isEqualTo("EVERYONE");
        assertThat(persisted.numberOfPlayers()).isEqualTo(3);
        assertThat(persisted.trumpSuit()).isEqualTo(game.getTrumpSuit().name());
        assertThat(persisted.rounds()).isNotEmpty();
        assertThat(persisted.finishOrderUserIds()).isEqualTo(finishOrder);
        assertThat(persisted.draw()).isEqualTo(game.isDraw());
        if (game.isDraw()) {
            assertThat(persisted.loserUserId()).isNull();
        } else {
            assertThat(persisted.loserUserId()).isEqualTo((long) game.getLoser().getSeat() + 1);
        }

        // Every bout stores an explicit outcome rather than a winner, and every play stores its role.
        for (var round : persisted.rounds()) {
            assertThat(round.winner()).isNull();
            assertThat(round.attributes()).containsKey("outcome");
            assertThat(round.attributes().get("outcome")).isIn("DEFENDED", "TAKEN");
            for (var play : round.plays()) {
                assertThat(play.actionType()).isIn("ATTACK", "DEFEND", "THROW_IN", "PASS");
                if ("DEFEND".equals(play.actionType())) {
                    assertThat(play.targetPlayOrder()).isNotNull();
                }
            }
        }
        assertThat(persisted.rounds().stream()
                .flatMap(round -> round.plays().stream())
                .anyMatch(play -> "ATTACK".equals(play.actionType()))).isTrue();

        // The detailed history DTO reconstructs from the reloaded aggregate alone.
        var history = historyService.getGameHistory(recording.id());
        assertThat(history).isNotNull();
        assertThat(history.getGameConfig().getModeKey()).isEqualTo(CONFIG.modeKey());
        assertThat(history.getGameConfig().getDeckSize()).isEqualTo(54);
        assertThat(history.getGameConfig().getJokersEnabled()).isTrue();
        assertThat(history.getBouts()).hasSameSizeAs(persisted.rounds());
        assertThat(history.getBouts().getFirst().getInitialAttacker()).isNotNull();
        assertThat(history.getBouts().getFirst().getFinalDefender()).isNotNull();
        assertThat(history.getBouts().getFirst().getOutcome()).isIn("DEFENDED", "TAKEN");
        assertThat(history.getTrumpSuit()).isEqualTo(game.getTrumpSuit().name());
        assertThat(history.isDraw()).isEqualTo(game.isDraw());
        if (game.isDraw()) {
            assertThat(history.getWinners()).isEmpty();
            assertThat(history.getLoser()).isNull();
        } else {
            assertThat(history.getWinners()).hasSize(CONFIG.numberOfPlayers() - 1);
            assertThat(history.getLoser()).isNotNull();
            assertThat(history.getWinners()).doesNotContain(history.getLoser());
        }
        for (var bout : history.getBouts()) {
            for (var play : bout.getPlays()) {
                assertThat(play.getCard()).isNotNull();
                assertThat(play.getPlayer()).isNotNull();
            }
        }
    }

    @Test
    void existingTresetaHistoryStillReadsBackWithThePlayActionType() {
        var game = new com.ultracards.games.treseta.TresetaGame(
                new ArrayList<>(List.of(new com.ultracards.games.treseta.TresetaPlayer("one"),
                        new com.ultracards.games.treseta.TresetaPlayer("two"))),
                com.ultracards.games.treseta.TresetaGameConfig.TWO_PLAYERS);
        var recorder = new com.ultracards.recorder.TresetaGameRecorder(UUID.randomUUID(), UUID.randomUUID(),
                "regression", 1L, com.ultracards.games.treseta.TresetaGameConfig.TWO_PLAYERS.name(), false,
                List.of(), player -> new RecordedPlayer("one".equals(player.getName()) ? 1L : 2L,
                        player.getName()));
        recorder.attach(game);
        game.start();
        for (var ignored : List.of(1, 2)) { // a full trick, so the round closes and gets recorded
            var field = game.getPlayingField();
            var player = field.getCurrentPlayer();
            var leadSuit = field.getPlayedCards().isEmpty() ? null : field.getPlayedCards().getFirst().getSuit();
            var card = player.getHand().getCards().stream()
                    .filter(candidate -> leadSuit == null || candidate.getSuit().equals(leadSuit)
                            || !player.getHand().containsSuit(leadSuit))
                    .findFirst().orElseThrow();
            field.play(card, player);
        }

        var recording = recorder.recording();
        tresetaRepository.saveAndFlush(recording);
        entityManager.clear();

        var persisted = tresetaRepository.findById(recording.id()).orElseThrow();
        assertThat(persisted.rounds().getFirst().plays()).isNotEmpty();
        assertThat(persisted.rounds().getFirst().plays().getFirst().actionType()).isEqualTo(RecordedPlay.PLAY);
        assertThat(persisted.rounds().getFirst().plays().getFirst().targetPlayOrder()).isNull();
    }

    @Test
    void unfinishedDurakGameIsNotExposedAsHistory() {
        var players = new ArrayList<DurakPlayer>();
        for (int seat = 0; seat < CONFIG.numberOfPlayers(); seat++) {
            players.add(new DurakPlayer("P" + seat, seat));
        }
        var game = new DurakGame(players, CONFIG, new Random(7));
        var recorder = new DurakGameRecorder(UUID.randomUUID(), UUID.randomUUID(), "unfinished", 1L, CONFIG,
                player -> new RecordedPlayer((long) (((DurakPlayer) player).getSeat() + 1), player.getName()));
        recorder.attach(game);
        game.start();

        var recording = repository.saveAndFlush(recorder.recording());
        entityManager.clear();
        var user = new UserEntity();
        user.setId(1L);

        assertThat(historyService.getPastGames(user, "", "latest")).isEmpty();
        assertThat(historyService.getPastGames(user, "", "oldest")).isEmpty();
        assertThat(historyService.getGameHistory(recording.id())).isNull();
    }

    private void playOut(DurakGame game) {
        var guard = 0;
        while (game.isGameActive()) {
            if (++guard > 20_000) throw new IllegalStateException("game did not terminate");
            var field = game.getPlayingField();
            var actor = field.getActionPlayer();
            game.apply(actor, next(game, field, actor));
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
