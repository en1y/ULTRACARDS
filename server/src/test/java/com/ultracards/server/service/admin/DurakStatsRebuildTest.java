package com.ultracards.server.service.admin;

import com.ultracards.games.durak.DurakGameConfig;
import com.ultracards.games.durak.DurakThrowInPolicy;
import com.ultracards.recorder.RecordedDurakGame;
import com.ultracards.recorder.RecordedPlayer;
import com.ultracards.server.entity.UserEntity;
import com.ultracards.server.repositories.UserRepository;
import com.ultracards.server.repositories.games.DurakGameRepository;
import com.ultracards.server.repositories.games.UserDurakStatsRepository;
import com.ultracards.server.service.games.UserGamesStatsService;
import com.ultracards.server.service.games.durak.UserDurakStatsService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The recorded outcome is the source of truth: rebuilding Durak statistics from history must
 * produce exactly what the live finish path wrote.
 */
@SpringBootTest(properties = {
        "spring.main.web-application-type=none",
        "app.database.startup-check.enabled=false",
        "app.mail.startup-check.enabled=false"
})
@Transactional
class DurakStatsRebuildTest {
    @Autowired private AdminStatsService adminStatsService;
    @Autowired private UserRepository users;
    @Autowired private DurakGameRepository durakGames;
    @Autowired private UserDurakStatsRepository durakStats;
    @Autowired private UserDurakStatsService userDurakStatsService;
    @Autowired private UserGamesStatsService userGamesStatsService;
    @Autowired private EntityManager entityManager;

    private static final DurakGameConfig CONFIG =
            new DurakGameConfig(6, 36, false, DurakThrowInPolicy.EVERYONE, true);

    @Test
    void liveCountersMatchARebuildFromRecordedHistory() {
        var players = new ArrayList<UserEntity>();
        for (int i = 0; i < CONFIG.numberOfPlayers(); i++) {
            players.add(user("dr" + i + "-" + shortId()));
        }
        var loser = players.getLast();
        var modeKey = CONFIG.modeKey();

        // One completed game recorded exactly as DurakGameService.finish would write it.
        var recording = new RecordedDurakGame(UUID.randomUUID(), UUID.randomUUID(), "rebuild", players.getFirst().getId(),
                modeKey, CONFIG.numberOfPlayers(), CONFIG.deckSize(), CONFIG.jokersEnabled(),
                CONFIG.throwInPolicy().name(), CONFIG.passingEnabled());
        var recorded = new ArrayList<RecordedPlayer>();
        for (var player : players) recorded.add(new RecordedPlayer(player.getId(), player.getUsername()));
        startAndEnd(recording, recorded);
        var finishOrder = new ArrayList<Long>();
        for (var player : players) if (!player.equals(loser)) finishOrder.add(player.getId());
        recording.result("SPADES", "S6", loser.getId(), false, finishOrder);
        durakGames.saveAndFlush(recording);

        // Live path: five winners and one durak.
        for (var player : players) {
            var won = !player.equals(loser);
            userGamesStatsService.addGame(player, com.ultracards.server.enums.games.GameType.DURAK, won);
            userDurakStatsService.addDurakGame(player, modeKey, won);
            if (!won) userDurakStatsService.addTimesDurak(player);
            for (var other : players) {
                if (other.equals(player)) continue;
                userDurakStatsService.addGameAgainstUser(player, modeKey, other, won && other.equals(loser));
            }
        }
        entityManager.flush();

        var live = new ArrayList<String>();
        for (var player : players) live.add(describe(player));

        var rebuilt = new ArrayList<String>();
        for (var player : players) {
            adminStatsService.rebuild(players.getFirst(), player.getId(), "DURAK", "verify rebuild", false);
            entityManager.flush();
            entityManager.clear();
            rebuilt.add(describe(users.findById(player.getId()).orElseThrow()));
        }

        assertThat(rebuilt).containsExactlyElementsOf(live);
        // Five winners, one durak, and nobody credited with beating a fellow winner.
        assertThat(rebuilt.getFirst()).contains("played=1", "wins=1", "timesDurak=0", "draws=0", "beat=1");
        assertThat(rebuilt.getLast()).contains("played=1", "wins=0", "timesDurak=1", "beat=0");
    }

    @Test
    void aDrawCountsAsPlayedForEverybodyAndAWinForNobody() {
        var one = user("dd1-" + shortId());
        var two = user("dd2-" + shortId());
        var config = new DurakGameConfig(2, 24, false, DurakThrowInPolicy.NEIGHBORS_ONLY, false);
        var recording = new RecordedDurakGame(UUID.randomUUID(), UUID.randomUUID(), "draw", one.getId(),
                config.modeKey(), 2, 24, false, config.throwInPolicy().name(), false);
        startAndEnd(recording, List.of(new RecordedPlayer(one.getId(), one.getUsername()),
                new RecordedPlayer(two.getId(), two.getUsername())));
        recording.result("SPADES", "S9", null, true, List.of(one.getId(), two.getId()));
        durakGames.saveAndFlush(recording);

        for (var player : List.of(one, two)) {
            adminStatsService.rebuild(one, player.getId(), "DURAK", "draw rebuild", false);
        }
        entityManager.flush();
        entityManager.clear();

        for (var player : List.of(one, two)) {
            var stats = durakStats.findByUser(users.findById(player.getId()).orElseThrow()).orElseThrow();
            assertThat(stats.getConfigStats().get(config.modeKey()).getPlayed()).isEqualTo(1);
            assertThat(stats.getConfigStats().get(config.modeKey()).getWins()).isZero();
            assertThat(stats.getDraws()).isEqualTo(1);
            assertThat(stats.getTimesDurak()).isZero();
        }
    }

    @Test
    void aCompletedGameWithoutAValidLoserCannotBeRebuiltAsWins() {
        var one = user("dci1-" + shortId());
        var two = user("dci2-" + shortId());
        var config = new DurakGameConfig(2, 24, false, DurakThrowInPolicy.NEIGHBORS_ONLY, false);
        var recording = new RecordedDurakGame(UUID.randomUUID(), UUID.randomUUID(), "corrupt", one.getId(),
                config.modeKey(), 2, 24, false, config.throwInPolicy().name(), false);
        startAndEnd(recording, List.of(new RecordedPlayer(one.getId(), one.getUsername()),
                new RecordedPlayer(two.getId(), two.getUsername())));
        recording.result("SPADES", "S9", null, false, List.of(one.getId()));
        durakGames.saveAndFlush(recording);

        assertThatThrownBy(() -> adminStatsService.rebuild(
                one, one.getId(), "DURAK", "integrity check", false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("has no loser");
    }

    @Test
    void anUnknownDurakModeInAnAdminOverrideIsRejected() {
        var user = user("dbm-" + shortId());
        assertThatThrownBy(() -> adminStatsService.patch(user, user.getId(), "DURAK", "P9_D99",
                new com.ultracards.gateway.dto.admin.AdminStatsPatchDTO(1, 1, null, "why", true)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    private String describe(UserEntity user) {
        var stats = durakStats.findByUser(user).orElseThrow();
        var lines = new ArrayList<String>();
        stats.getConfigStats().forEach((mode, value) ->
                lines.add(mode + ":played=" + value.getPlayed() + ",wins=" + value.getWins()));
        lines.sort(String::compareTo);
        var beat = stats.getWinsAgainstUser().stream().mapToInt(row -> row.getWins()).sum();
        var playedAgainst = stats.getWinsAgainstUser().stream().mapToInt(row -> row.getPlayed()).sum();
        return String.join("|", lines) + " timesDurak=" + stats.getTimesDurak() + " draws=" + stats.getDraws()
                + " beat=" + beat + " playedAgainst=" + playedAgainst;
    }

    private void startAndEnd(RecordedDurakGame recording, List<RecordedPlayer> players) {
        // started()/ended() are package-private on RecordedGame, so drive them through a recorder-free path.
        org.springframework.test.util.ReflectionTestUtils.invokeMethod(recording, "started", players,
                java.util.Map.<String, String>of());
        org.springframework.test.util.ReflectionTestUtils.invokeMethod(recording, "ended",
                java.util.Map.<String, String>of());
    }

    private static String shortId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private UserEntity user(String username) {
        return users.saveAndFlush(new UserEntity(username + "@example.com", username));
    }
}
