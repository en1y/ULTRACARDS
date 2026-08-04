package com.ultracards.server.service.leaderboard;

import com.ultracards.games.briskula.BriskulaGameConfig;
import com.ultracards.gateway.dto.leaderboard.LeaderboardMetricDTO;
import com.ultracards.recorder.RecordedBriskulaGame;
import com.ultracards.server.entity.UserEntity;
import com.ultracards.server.entity.games.gamestats.GameStats;
import com.ultracards.server.entity.games.gamestats.UserBriskulaStats;
import com.ultracards.server.entity.games.gamestats.UserGamesStats;
import com.ultracards.server.enums.games.GameType;
import com.ultracards.server.repositories.UserRepository;
import com.ultracards.server.repositories.games.BriskulaGameRepository;
import com.ultracards.server.repositories.games.UserBriskulaStatsRepository;
import com.ultracards.server.repositories.games.UserGamesStatsRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "spring.main.web-application-type=none",
        "app.database.startup-check.enabled=false",
        "app.mail.startup-check.enabled=false"
})
@Transactional
class LeaderboardServicePersistenceTest {
    @Autowired private LeaderboardService service;
    @Autowired private UserRepository users;
    @Autowired private UserGamesStatsRepository overallStats;
    @Autowired private UserBriskulaStatsRepository briskulaStats;
    @Autowired private BriskulaGameRepository games;
    @Autowired private com.ultracards.server.repositories.games.UserDurakStatsRepository durakStats;

    @Test
    void ranksTiesDeterministicallyAndReturnsTheCurrentUsersPosition() {
        var suffix = UUID.randomUUID().toString();
        var first = user("leaderboard-a-" + suffix);
        var second = user("leaderboard-z-" + suffix);
        overall(first, 2_000_000_000, 1_000_000_000);
        overall(second, 2_000_000_000, 1_000_000_000);

        var firstPage = service.get("games-played", null, null, 0, 1, second);
        var secondPage = service.get("games-played", null, null, 1, 1, second);

        assertThat(firstPage.items()).singleElement().satisfies(entry -> {
            assertThat(entry.userId()).isEqualTo(first.getId());
            assertThat(entry.position()).isEqualTo(1L);
            assertThat(entry.currentUser()).isFalse();
        });
        assertThat(secondPage.items()).singleElement().satisfies(entry -> {
            assertThat(entry.userId()).isEqualTo(second.getId());
            assertThat(entry.position()).isEqualTo(2L);
            assertThat(entry.currentUser()).isTrue();
        });
        assertThat(firstPage.totalPages()).isGreaterThanOrEqualTo(2);
        assertThat(firstPage.currentUserPosition()).isEqualTo(2L);
        assertThat(secondPage.currentUserPosition()).isEqualTo(2L);
    }

    @Test
    void appliesTheWinRateMinimumAndGameFilter() {
        var qualified = user("qualified-" + UUID.randomUUID());
        var tooFewGames = user("too-few-" + UUID.randomUUID());
        overall(qualified, 10, 8);
        overall(tooFewGames, 9, 9);

        var result = service.get("WIN_RATE", "Briskula", null, 0, 100, qualified);

        assertThat(result.minimumGames()).isEqualTo(10);
        assertThat(result.items()).extracting(entry -> entry.userId()).contains(qualified.getId());
        assertThat(result.items()).extracting(entry -> entry.userId()).doesNotContain(tooFewGames.getId());
    }

    @Test
    void acceptsThePersistedTresetaGameTypeSpelling() {
        var user = user("treseta-" + UUID.randomUUID());
        var stats = new UserGamesStats(user);
        stats.getGameStats().put(GameType.TRESETA, new GameStats(12, 7));
        overallStats.saveAndFlush(stats);

        var result = service.get("GAMES_PLAYED", "Treseta", null, 0, 100, user);

        assertThat(result.items()).filteredOn(entry -> entry.userId().equals(user.getId()))
                .singleElement()
                .satisfies(entry -> assertThat(entry.gamesPlayed()).isEqualTo(12));
    }

    @Test
    void modeRankingsReadCorrectedStatisticsImmediately() {
        var user = user("corrected-" + UUID.randomUUID());
        var stats = new UserBriskulaStats(user);
        stats.getConfigStats().put(BriskulaGameConfig.TWO_PLAYERS, new GameStats(20, 10));
        briskulaStats.saveAndFlush(stats);

        stats.getConfigStats().put(BriskulaGameConfig.TWO_PLAYERS, new GameStats(30, 29));
        briskulaStats.saveAndFlush(stats);
        var result = service.get("WIN_RATE", "Briskula", "TWO_PLAYERS", 0, 100, user);

        assertThat(result.mode()).isEqualTo("TWO_PLAYERS");
        assertThat(result.availableModes()).contains(BriskulaGameConfig.TWO_PLAYERS.name());
        assertThat(result.items()).filteredOn(entry -> entry.userId().equals(user.getId()))
                .singleElement()
                .satisfies(entry -> {
                    assertThat(entry.gamesPlayed()).isEqualTo(30);
                    assertThat(entry.wins()).isEqualTo(29);
                    assertThat(entry.winRate()).isCloseTo(96.67, org.assertj.core.data.Offset.offset(0.01));
                });
    }

    @Test
    void incompleteRecordingsDoNotCreateLeaderboardRows() {
        var user = user("incomplete-" + UUID.randomUUID());
        games.saveAndFlush(new RecordedBriskulaGame(UUID.randomUUID(), UUID.randomUUID(), "unfinished",
                user.getId(), BriskulaGameConfig.TWO_PLAYERS.name(), false, List.of()));

        var result = service.get(LeaderboardMetricDTO.GAMES_PLAYED.name(), "Briskula", null, 0, 100, null);

        assertThat(result.items()).extracting(entry -> entry.userId()).doesNotContain(user.getId());
        assertThat(result.currentUserPosition()).isNull();
    }

    @Test
    void ranksDurakOverallAndPerMode() {
        var winner = user("durak-win-" + UUID.randomUUID());
        var durak = user("durak-lose-" + UUID.randomUUID());
        var mode = "P3_D36_NO_JOKERS_EVERYONE_PASS";

        // A three-player game creates two winners and one durak.
        var winnerOverall = new UserGamesStats(winner);
        winnerOverall.getGameStats().put(GameType.DURAK, new GameStats(12, 12));
        overallStats.saveAndFlush(winnerOverall);
        var durakOverall = new UserGamesStats(durak);
        durakOverall.getGameStats().put(GameType.DURAK, new GameStats(12, 0));
        overallStats.saveAndFlush(durakOverall);

        var winnerModes = new com.ultracards.server.entity.games.gamestats.UserDurakStats(winner);
        winnerModes.getConfigStats().put(mode, new GameStats(12, 12));
        durakStats.saveAndFlush(winnerModes);
        var durakModes = new com.ultracards.server.entity.games.gamestats.UserDurakStats(durak);
        durakModes.getConfigStats().put(mode, new GameStats(12, 0));
        durakModes.setTimesDurak(12);
        durakStats.saveAndFlush(durakModes);

        var overallRanking = service.get("WINS", "Durak", null, 0, 100, winner);
        assertThat(overallRanking.items()).filteredOn(entry -> entry.userId().equals(winner.getId()))
                .singleElement().satisfies(entry -> {
                    assertThat(entry.wins()).isEqualTo(12);
                    assertThat(entry.gamesPlayed()).isEqualTo(12);
                });
        assertThat(overallRanking.items()).filteredOn(entry -> entry.userId().equals(durak.getId()))
                .singleElement().satisfies(entry -> assertThat(entry.wins()).isZero());

        var modeRanking = service.get("WIN_RATE", "Durak", mode, 0, 100, winner);
        assertThat(modeRanking.mode()).isEqualTo(mode);
        assertThat(modeRanking.availableModes()).contains(mode);
        assertThat(modeRanking.items()).filteredOn(entry -> entry.userId().equals(winner.getId()))
                .singleElement().satisfies(entry -> assertThat(entry.winRate()).isEqualTo(100.0));
        assertThat(modeRanking.items()).filteredOn(entry -> entry.userId().equals(durak.getId()))
                .singleElement().satisfies(entry -> assertThat(entry.winRate()).isZero());

        // The 54-card pack with and without Jokers rank as separate modes.
        var withJokers = service.get("WINS", "Durak", "P3_D54_JOKERS_EVERYONE_PASS", 0, 100, winner);
        assertThat(withJokers.items()).extracting(entry -> entry.userId()).doesNotContain(winner.getId());
    }

    /** A rule filter covers several modes at once, and the board is their sum. */
    @Test
    void sumsDurakModeSets() {
        var player = user("durak-set-" + UUID.randomUUID());
        var modes = new com.ultracards.server.entity.games.gamestats.UserDurakStats(player);
        modes.getConfigStats().put("P3_D36_NO_JOKERS_EVERYONE_PASS", new GameStats(4, 3));
        modes.getConfigStats().put("P3_D54_NO_JOKERS_EVERYONE_PASS", new GameStats(6, 1));
        modes.getConfigStats().put("P4_D36_NO_JOKERS_EVERYONE_PASS", new GameStats(9, 9));
        durakStats.saveAndFlush(modes);

        var threePlayer = service.get("GAMES_PLAYED", "Durak", null,
                List.of("P3_D36_NO_JOKERS_EVERYONE_PASS", "P3_D54_NO_JOKERS_EVERYONE_PASS"), 0, 100, player);
        assertThat(threePlayer.items()).filteredOn(entry -> entry.userId().equals(player.getId()))
                .singleElement().satisfies(entry -> {
                    assertThat(entry.gamesPlayed()).isEqualTo(10);
                    assertThat(entry.wins()).isEqualTo(4);
                });

        var commaSeparated = service.get("GAMES_PLAYED", "Durak", null,
                List.of("P3_D36_NO_JOKERS_EVERYONE_PASS,P4_D36_NO_JOKERS_EVERYONE_PASS"), 0, 100, player);
        assertThat(commaSeparated.items()).filteredOn(entry -> entry.userId().equals(player.getId()))
                .singleElement().satisfies(entry -> assertThat(entry.gamesPlayed()).isEqualTo(13));

        assertThatThrownBy(() -> service.get("WINS", "Durak", null, List.of("NOPE"), 0, 25, player))
                .isInstanceOf(ResponseStatusException.class);
    }

    private UserEntity user(String username) {
        return users.saveAndFlush(new UserEntity(username + "@example.com", username));
    }

    private void overall(UserEntity user, int played, int wins) {
        var stats = new UserGamesStats(user);
        stats.getGameStats().put(GameType.BRISKULA, new GameStats(played, wins));
        overallStats.saveAndFlush(stats);
    }
}
