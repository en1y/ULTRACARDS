package com.ultracards.server.service.points;

import com.ultracards.gateway.dto.games.lobby.WagerConfigDTO;
import com.ultracards.gateway.dto.admin.AdminPointEventPatchDTO;
import com.ultracards.gateway.dto.points.PointsAchievementDTO;
import com.ultracards.server.entity.UserEntity;
import com.ultracards.server.enums.games.GameType;
import com.ultracards.server.repositories.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "spring.main.web-application-type=none",
        "app.database.startup-check.enabled=false",
        "app.mail.startup-check.enabled=false"
})
@Transactional
class PointsServicePersistenceTest {
    @Autowired private PointsService points;
    @Autowired private UserRepository users;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper objectMapper;

    @Test
    void initializesAndClaimsTheDailyReserveOnlyBelowTheThreshold() {
        var user = user("daily");

        assertThat(points.summary(user).balance()).isEqualTo(1_500);
        assertThat(points.summary(user).nextClaimAt()).isNull();
        assertThatThrownBy(() -> points.claimDaily(user)).isInstanceOf(ResponseStatusException.class);

        points.adjust(user.getId(), -1_001, "TEST-SPEND");
        var claim = points.claimDaily(user);

        assertThat(claim.awarded()).isEqualTo(1_500);
        assertThat(claim.account().balance()).isEqualTo(1_999);
        assertThat(claim.account().dailyClaimAvailable()).isFalse();
        assertThat(claim.account().nextClaimAt()).isNotNull();
        assertThatThrownBy(() -> points.claimDaily(user)).isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void settlesThreePlayerWagerWithNinetySixPercentOfTheLosingPool() {
        var first = user("winner-a");
        var second = user("winner-b");
        var loser = user("loser");
        var gameId = UUID.randomUUID();
        var participants = List.of(first, second, loser);

        points.updateWagerFeePercent(4, first.getId());
        points.reserveWager(gameId, UUID.randomUUID(), new WagerConfigDTO(true, 100), participants);
        points.completeGame(gameId, participants, Set.of(first.getId(), second.getId()));

        var payouts = jdbc.queryForList("""
                SELECT amount FROM point_transactions
                WHERE transaction_type = 'WAGER_PAYOUT' AND reference_id = ? ORDER BY user_id
                """, Long.class, gameId.toString());
        assertThat(payouts).containsExactly(148L, 148L);
        assertThat(jdbc.queryForObject("SELECT rake_points FROM point_wagers WHERE game_id = ?", Long.class, gameId))
                .isEqualTo(4L);
        assertThat(points.wagerPayouts(gameId)).containsExactlyInAnyOrderEntriesOf(
                java.util.Map.of(first.getId(), 148L, second.getId(), 148L));
        assertThat(points.wagerDeltas(gameId)).containsExactlyInAnyOrderEntriesOf(java.util.Map.of(
                first.getId(), 48L,
                second.getId(), 48L,
                loser.getId(), -100L));
    }

    @Test
    void snapshotsTheConfiguredFeeWhenTheWagerIsReserved() {
        var winner = user("fee-winner");
        var loser = user("fee-loser");
        var firstGame = UUID.randomUUID();
        points.updateWagerFeePercent(10, winner.getId());
        points.reserveWager(firstGame, UUID.randomUUID(), new WagerConfigDTO(true, 100), List.of(winner, loser));

        points.updateWagerFeePercent(50, winner.getId());
        points.completeGame(firstGame, List.of(winner, loser), Set.of(winner.getId()), GameType.DURAK);

        assertThat(jdbc.queryForObject("SELECT fee_percent FROM point_wagers WHERE game_id = ?", Integer.class, firstGame))
                .isEqualTo(10);
        assertThat(jdbc.queryForObject("SELECT rake_points FROM point_wagers WHERE game_id = ?", Long.class, firstGame))
                .isEqualTo(10L);

        var secondGame = UUID.randomUUID();
        points.reserveWager(secondGame, UUID.randomUUID(), new WagerConfigDTO(true, 100), List.of(winner, loser));
        assertThat(jdbc.queryForObject("SELECT fee_percent FROM point_wagers WHERE game_id = ?", Integer.class, secondGame))
                .isEqualTo(50);
    }

    @Test
    void prefersTheModeFeeThenTheGameFeeThenTheGlobalOne() {
        var winner = user("mode-fee-winner");
        var loser = user("mode-fee-loser");
        points.updateWagerFeePercent(4, winner.getId());
        points.setWagerFee(GameType.DURAK, null, 10);
        points.setWagerFee(GameType.DURAK, "P2_D36_NO_JOKERS_NEIGHBORS_NO_PASS", 25);

        assertThat(feeOf(winner, loser, GameType.DURAK, "P2_D36_NO_JOKERS_NEIGHBORS_NO_PASS")).isEqualTo(25);
        assertThat(feeOf(winner, loser, GameType.DURAK, "P3_D36_NO_JOKERS_NEIGHBORS_NO_PASS")).isEqualTo(10);
        assertThat(feeOf(winner, loser, GameType.BRISKULA, "TWO_PLAYERS")).isEqualTo(4);

        points.resetWagerFee(GameType.DURAK, "P2_D36_NO_JOKERS_NEIGHBORS_NO_PASS");
        assertThat(feeOf(winner, loser, GameType.DURAK, "P2_D36_NO_JOKERS_NEIGHBORS_NO_PASS")).isEqualTo(10);
        points.resetWagerFee(GameType.DURAK, null);
        assertThat(feeOf(winner, loser, GameType.DURAK, "P2_D36_NO_JOKERS_NEIGHBORS_NO_PASS")).isEqualTo(4);
    }

    private int feeOf(UserEntity first, UserEntity second, GameType gameType, String mode) {
        var gameId = UUID.randomUUID();
        points.reserveWager(gameId, UUID.randomUUID(), new WagerConfigDTO(true, 100), List.of(first, second),
                gameType, mode);
        return jdbc.queryForObject("SELECT fee_percent FROM point_wagers WHERE game_id = ?", Integer.class, gameId);
    }

    @Test
    void buildsTheAdminEconomyDashboardFromBalancesLedgerAndWagers() {
        var before = points.adminDashboard();
        var first = user("dashboard-first");
        var second = user("dashboard-second");
        try {
            points.updateWagerFeePercent(7, first.getId());
            points.reserveWager(UUID.randomUUID(), UUID.randomUUID(), new WagerConfigDTO(true, 100), List.of(first, second));
            users.flush();

            var dashboard = points.adminDashboard();

            assertThat(dashboard.wagerFeePercent()).isEqualTo(7);
            assertThat(dashboard.pointsInAccounts()).isEqualTo(before.pointsInAccounts() + 2_800);
            assertThat(dashboard.pointsInEscrow()).isEqualTo(before.pointsInEscrow() + 200);
            assertThat(dashboard.pointsInCirculation()).isEqualTo(before.pointsInCirculation() + 3_000);
            assertThat(dashboard.totalWagers()).isEqualTo(before.totalWagers() + 1);
            assertThat(dashboard.openWagers()).isEqualTo(before.openWagers() + 1);
            assertThat(dashboard.biggestWager()).isGreaterThanOrEqualTo(100);
            assertThat(dashboard.totalStaked()).isEqualTo(before.totalStaked() + 200);
            assertThat(dashboard.dailyTotals()).hasSize(30);
            assertThat(dashboard.leaderboard()).hasSizeLessThanOrEqualTo(10)
                    .extracting(item -> item.points()).isSortedAccordingTo(java.util.Comparator.reverseOrder());
        } finally {
            points.updateWagerFeePercent(before.wagerFeePercent(), first.getId());
        }
    }

    @Test
    void scheduledEventCountsWinsLossesAndDrawsAndAwardsOnlyOnce() {
        var user = user("event-player");
        var opponent = user("event-opponent");
        var third = user("event-third");
        var goal = new AdminPointEventPatchDTO.Achievement(null, "Durak set", "Do all three",
                3, 1, 1, 1, 200L, List.of("DURAK"), false);
        var event = points.createEvent(new AdminPointEventPatchDTO("Durak weekend", "", Instant.now().minusSeconds(60),
                Instant.now().plusSeconds(3_600), List.of("DURAK"), 300L, true, List.of(goal), "test"));

        points.completeGame(UUID.randomUUID(), List.of(user, opponent), Set.of(), GameType.DURAK);
        points.completeGame(UUID.randomUUID(), List.of(user, opponent), Set.of(opponent.getId()), GameType.DURAK);
        points.completeGame(UUID.randomUUID(), List.of(user, opponent, third),
                Set.of(user.getId(), opponent.getId()), GameType.DURAK);
        points.summary(user);

        var shown = points.events(user).stream().filter(item -> item.id().equals(event.id())).findFirst().orElseThrow();
        assertThat(shown.active()).isTrue();
        assertThat(shown.achievements()).singleElement().satisfies(achievement -> {
            assertThat(achievement.completed()).isTrue();
            assertThat(achievement.games()).isEqualTo(3);
            assertThat(achievement.wins()).isEqualTo(1);
            assertThat(achievement.losses()).isEqualTo(1);
            assertThat(achievement.draws()).isEqualTo(1);
        });
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM point_transactions
                WHERE user_id = ? AND transaction_type IN ('EVENT_ACHIEVEMENT', 'EVENT_COMPLETION')
                  AND reference_id IN (?, ?)
                """, Long.class, user.getId(), event.id().toString(),
                event.achievements().getFirst().id().toString())).isEqualTo(2L);
        assertThat(jdbc.queryForObject("""
                SELECT COALESCE(SUM(amount), 0) FROM point_transactions
                WHERE user_id = ? AND transaction_type IN ('EVENT_ACHIEVEMENT', 'EVENT_COMPLETION')
                  AND reference_id IN (?, ?)
                """, Long.class, user.getId(), event.id().toString(),
                event.achievements().getFirst().id().toString())).isEqualTo(500L);
    }

    @Test
    void keepsAFinishedEventInHistoryOnlyForThePlayersWhoTookPart() {
        var user = user("history-player");
        var opponent = user("history-opponent");
        var bystander = user("history-bystander");
        var goal = new AdminPointEventPatchDTO.Achievement(null, "Play once", "", 1, 0, 0, 0,
                200L, List.of("DURAK"), false);
        var event = points.createEvent(new AdminPointEventPatchDTO("Last weekend", "",
                Instant.now().minusSeconds(3_600), Instant.now().plusSeconds(3_600), List.of("DURAK"),
                300L, true, List.of(goal), "test"));

        points.completeGame(UUID.randomUUID(), List.of(user, opponent), Set.of(user.getId()), GameType.DURAK);
        // Back-date the games and then close the window, so the event reads as one
        // that ran and finished with these two players in it.
        jdbc.update("UPDATE point_game_results SET ended_at = ? WHERE user_id IN (?, ?)",
                Timestamp.from(Instant.now().minusSeconds(600)), user.getId(), opponent.getId());
        jdbc.update("UPDATE point_events SET ends_at = ? WHERE id = ?",
                Timestamp.from(Instant.now().minusSeconds(300)), event.id());

        var history = points.events(user).stream().filter(item -> item.id().equals(event.id()))
                .findFirst().orElseThrow();
        assertThat(history.active()).isFalse();
        assertThat(history.earnedPoints()).isEqualTo(500L);
        assertThat(history.achievements()).singleElement()
                .satisfies(achievement -> assertThat(achievement.completed()).isTrue());
        assertThat(points.events(bystander)).noneMatch(item -> item.id().equals(event.id()));
    }

    @Test
    void ordersPlayerEventsByActiveStatusThenNewestStart() {
        var user = user("ordered-events");
        var goal = new AdminPointEventPatchDTO.Achievement(null, "Play once", "", 1, 0, 0, 0,
                0L, List.of(), false);
        var now = Instant.now();
        var activeOlder = points.createEvent(new AdminPointEventPatchDTO("Active older", "",
                now.minusSeconds(300), now.plusSeconds(3_600), List.of(), 0L, true, List.of(goal), "test"));
        var activeNewest = points.createEvent(new AdminPointEventPatchDTO("Active newest", "",
                now.minusSeconds(60), now.plusSeconds(3_600), List.of(), 0L, true, List.of(goal), "test"));
        var inactiveOlder = points.createEvent(new AdminPointEventPatchDTO("Inactive older", "",
                now.plusSeconds(300), now.plusSeconds(3_600), List.of(), 0L, true, List.of(goal), "test"));
        var inactiveNewest = points.createEvent(new AdminPointEventPatchDTO("Inactive newest", "",
                now.plusSeconds(600), now.plusSeconds(3_600), List.of(), 0L, true, List.of(goal), "test"));
        var ids = Set.of(activeOlder.id(), activeNewest.id(), inactiveOlder.id(), inactiveNewest.id());

        assertThat(points.events(user).stream().filter(event -> ids.contains(event.id())).map(event -> event.name()))
                .containsExactly("Active newest", "Active older", "Inactive newest", "Inactive older");
    }

    @Test
    void rejectsEventsWithoutAchievements() {
        assertThatThrownBy(() -> points.createEvent(new AdminPointEventPatchDTO("Tomorrow", "Notice only",
                Instant.now().plusSeconds(3_600), null, List.of(), 500L, true, List.of(), "test")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("at least one sub-achievement");

        var durakOnly = new AdminPointEventPatchDTO.Achievement(null, "Wrong game", "", 1, 0, 0, 0,
                10L, List.of("DURAK"), false);
        assertThatThrownBy(() -> points.createEvent(new AdminPointEventPatchDTO("Treseta event", "",
                Instant.now(), null, List.of("TRESETA"), 0L, true, List.of(durakOnly), "test")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("must be allowed by its event");
    }

    @Test
    void keepsHiddenAchievementsOpaqueUntilCompletion() throws Exception {
        var user = user("hidden-event-player");
        var opponent = user("hidden-event-opponent");
        var visible = new AdminPointEventPatchDTO.Achievement(null, "Play once", "", 1, 0, 0, 0,
                0L, List.of(), false);
        var hidden = new AdminPointEventPatchDTO.Achievement(null, "Durak secret", "Win at Durak",
                0, 1, 0, 0, 200L, List.of("DURAK"), true);
        var event = points.createEvent(new AdminPointEventPatchDTO("Mixed event", "", Instant.now().minusSeconds(60),
                Instant.now().plusSeconds(3_600), List.of("DURAK", "TRESETA"), 0L, true,
                List.of(visible, hidden), "test"));
        var hiddenId = event.achievements().get(1).id();

        points.completeGame(UUID.randomUUID(), List.of(user, opponent), Set.of(user.getId()), GameType.TRESETA);
        var shown = points.events(user).stream().filter(item -> item.id().equals(event.id()))
                .findFirst().orElseThrow();
        assertThat(shown.hiddenAchievementCount()).isOne();
        assertThat(shown.achievements()).singleElement()
                .satisfies(goal -> assertThat(goal.name()).isEqualTo("Play once"));
        assertThat(objectMapper.writeValueAsString(shown))
                .doesNotContain(hiddenId.toString(), "Durak secret", "Win at Durak", "\"rewardPoints\":200");
        assertThat(eventRewardCount(user.getId(), hiddenId)).isZero();
        assertThat(achievementNotificationCount(user, "Play once")).isOne();
        assertThat(achievementNotificationCount(user, "Durak secret")).isZero();

        points.completeGame(UUID.randomUUID(), List.of(user, opponent), Set.of(user.getId()), GameType.DURAK);
        shown = points.events(user).stream().filter(item -> item.id().equals(event.id()))
                .findFirst().orElseThrow();
        assertThat(shown.hiddenAchievementCount()).isZero();
        assertThat(shown.achievements()).extracting(goal -> goal.name())
                .containsExactly("Play once", "Durak secret");
        assertThat(shown.achievements().get(1).completed()).isTrue();
        assertThat(eventRewardCount(user.getId(), hiddenId)).isOne();
        assertThat(achievementNotificationCount(user, "Durak secret")).isOne();

        points.summary(user);
        assertThat(achievementNotificationCount(user, "Play once")).isOne();
        assertThat(achievementNotificationCount(user, "Durak secret")).isOne();
    }

    private long eventRewardCount(Long userId, UUID achievementId) {
        return jdbc.queryForObject("""
                SELECT COUNT(*) FROM point_transactions
                WHERE user_id = ? AND transaction_type = 'EVENT_ACHIEVEMENT' AND reference_id = ?
                """, Long.class, userId, achievementId.toString());
    }

    @Test
    void preservesAchievementIdentityWhenAnAdminClientUpdatesGoalsByPosition() {
        var goal = new AdminPointEventPatchDTO.Achievement(null, "Original", "", 1, 0, 0, 0, 10L,
                List.of(), false);
        var patch = new AdminPointEventPatchDTO("Editable", "", Instant.now().minusSeconds(60), null,
                List.of(), 0L, true, List.of(goal), "test");
        var event = points.createEvent(patch);
        var originalId = event.achievements().getFirst().id();

        var renamed = new AdminPointEventPatchDTO.Achievement(null, "Renamed", "", 2, 0, 0, 0, 20L,
                List.of(), false);
        var updated = points.updateEvent(event.id(), new AdminPointEventPatchDTO("Editable", "",
                patch.startsAt(), null, List.of(), 0L, true, List.of(renamed), "test"));

        assertThat(updated.achievements().getFirst().id()).isEqualTo(originalId);
    }

    @Test
    void deletesAnEventAndItsSubAchievements() {
        var goal = new AdminPointEventPatchDTO.Achievement(null, "Temporary goal", "", 1, 0, 0, 0,
                10L, List.of(), false);
        var event = points.createEvent(new AdminPointEventPatchDTO("Temporary event", "", Instant.now(), null,
                List.of(), 0L, true, List.of(goal), "test"));

        var deleted = points.deleteEvent(event.id());

        assertThat(deleted.name()).isEqualTo("Temporary event");
        assertThat(points.adminEvents()).noneMatch(item -> item.id().equals(event.id()));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM point_event_achievements WHERE event_id = ?",
                Long.class, event.id())).isZero();
    }

    @Test
    void refundsEveryStakeWhenNoWinnerIsReported() {
        var first = user("draw-none-a");
        var second = user("draw-none-b");
        var gameId = UUID.randomUUID();
        var participants = List.of(first, second);

        points.reserveWager(gameId, UUID.randomUUID(), new WagerConfigDTO(true, 100), participants);
        points.completeGame(gameId, participants, Set.of());

        assertRefunded(gameId);
    }

    @Test
    void refundsEveryStakeWhenEveryParticipantTies() {
        var first = user("draw-all-a");
        var second = user("draw-all-b");
        var gameId = UUID.randomUUID();
        var participants = List.of(first, second);

        points.reserveWager(gameId, UUID.randomUUID(), new WagerConfigDTO(true, 100), participants);
        points.completeGame(gameId, participants, Set.of(first.getId(), second.getId(), Long.MAX_VALUE));

        assertRefunded(gameId);
        assertThat(jdbc.queryForList("SELECT outcome FROM point_game_results WHERE game_id = ?", String.class, gameId))
                .containsOnly("DRAW");
    }

    @Test
    void gameRewardsAndSettlementAreIdempotent() {
        var winner = user("idempotent-winner");
        var loser = user("idempotent-loser");
        var gameId = UUID.randomUUID();
        var participants = List.of(winner, loser);
        points.reserveWager(gameId, UUID.randomUUID(), new WagerConfigDTO(true, 100), participants);

        points.completeGame(gameId, participants, Set.of(winner.getId()));
        var count = jdbc.queryForObject("SELECT COUNT(*) FROM point_transactions WHERE reference_id = ?",
                Long.class, gameId.toString());
        points.completeGame(gameId, participants, Set.of(winner.getId()));

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM point_transactions WHERE reference_id = ?",
                Long.class, gameId.toString())).isEqualTo(count);
    }

    @Test
    void rejectsAStakeThatAnyParticipantCannotCover() {
        var rich = user("rich");
        var poor = user("poor");
        points.adjust(poor.getId(), -1_500, "TEST-EMPTY");

        assertThatThrownBy(() -> points.reserveWager(UUID.randomUUID(), UUID.randomUUID(),
                new WagerConfigDTO(true, 100), List.of(rich, poor)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("poor");
    }

    @Test
    void reportsTheLockedBalanceChangedByAnAdjustment() {
        var user = user("adjusted");

        var adjustment = points.adjust(user.getId(), 125, "TEST-ADJUST");

        assertThat(adjustment.previous()).isEqualTo(1_500);
        assertThat(adjustment.current()).isEqualTo(1_625);
    }

    @Test
    void exposesOnlyTheFourFocusedAchievements() {
        var account = points.summary(user("achievements"));

        assertThat(account.achievements()).extracting(achievement -> achievement.code())
                .containsExactly("FIRST_GAME", "FIRST_WIN", "TEN_GAMES", "TEN_WINS");
    }

    @Test
    void loadsSevenDayChangesForAUserPageInOneBatch() {
        var first = user("batch-first");
        var second = user("batch-second");
        points.adjust(first.getId(), 25, "TEST-BATCH-FIRST");
        points.adjust(second.getId(), -50, "TEST-BATCH-SECOND");

        var changes = points.changesLast7Days(List.of(first.getId(), second.getId()));

        assertThat(changes).containsEntry(first.getId(), 1_525L).containsEntry(second.getId(), 1_450L);
    }

    @Test
    void playerFacingChangeUsesTheRollingLastTwentyFourHours() {
        var user = user("change-24h");
        points.adjust(user.getId(), 25, "TEST-CHANGE-24H");
        jdbc.update("""
                UPDATE point_transactions SET created_at = ?
                WHERE user_id = ? AND transaction_type = 'INITIAL_GRANT'
                """, Timestamp.from(Instant.now().minusSeconds(2 * 86_400L)), user.getId());

        assertThat(points.changeLast24Hours(user.getId())).isEqualTo(25L);
        assertThat(points.changesLast24Hours(List.of(user.getId()))).containsEntry(user.getId(), 25L);
        assertThat(points.summary(user).changeLast24Hours()).isEqualTo(25L);
    }

    @Test
    void countsOpenEscrowInTotalPointsSupply() {
        var first = user("supply-first");
        var second = user("supply-second");
        var gameId = UUID.randomUUID();
        var before = points.economySnapshot();

        points.reserveWager(gameId, UUID.randomUUID(), new WagerConfigDTO(true, 100), List.of(first, second));
        users.flush();
        var during = points.economySnapshot();

        assertThat(during.total()).isEqualTo(before.total());
        assertThat(during.escrowed()).isEqualTo(before.escrowed() + 200);

        points.cancelWager(gameId);
        users.flush();
        var after = points.economySnapshot();
        assertThat(after.total()).isEqualTo(before.total());
        assertThat(after.escrowed()).isEqualTo(before.escrowed());
    }

    @Test
    void achievementsTrackTodayAndAreStampedWithTheDay() {
        var user = user("daily-goals");
        assertThat(points.summary(user).achievements()).noneMatch(PointsAchievementDTO::earned);

        for (int game = 0; game < 10; game++) win(user);
        var account = points.summary(user);

        assertThat(account.achievements()).allMatch(PointsAchievementDTO::earned);
        assertThat(achievementCount(user)).isEqualTo(4);
        assertThat(achievementNotificationCount(user, "Play a game")).isOne();
        assertThat(achievementNotificationCount(user, "Win a game")).isOne();
        assertThat(achievementNotificationCount(user, "Play 10 games")).isOne();
        assertThat(achievementNotificationCount(user, "Win 10 games")).isOne();
        assertThat(achievementReferences(user)).allMatch(reference -> reference.endsWith(':' + today()));
        // Re-reading the page must not pay the same day's goals twice.
        var balance = account.balance();
        assertThat(points.summary(user).balance()).isEqualTo(balance);
        assertThat(achievementNotificationCount(user, "Play a game")).isOne();
    }

    @Test
    void yesterdaysGrantDoesNotBlockTodays() {
        var user = user("rollover");
        for (int game = 0; game < 10; game++) win(user);
        points.summary(user);
        var earnedToday = achievementCount(user);

        // Backdate today's grants by a day: the same goals must be collectable again.
        jdbc.update("""
                UPDATE point_transactions SET reference_id = replace(reference_id, ?, ?)
                WHERE user_id = ? AND transaction_type = 'ACHIEVEMENT'
                """, today(), LocalDate.parse(today()).minusDays(1).toString(), user.getId());
        points.summary(user);

        assertThat(achievementCount(user)).isEqualTo(earnedToday * 2);
    }

    /** One finished game the ledger's way: a GAME_PLAYED and a GAME_WON for the day. */
    private void win(UserEntity user) {
        points.completeGame(UUID.randomUUID(), List.of(user, user("daily-opponent")), Set.of(user.getId()));
    }

    private String today() {
        return LocalDate.now(ZoneId.of("Europe/Zagreb")).toString();
    }

    private long achievementCount(UserEntity user) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM point_transactions WHERE user_id = ? AND transaction_type = 'ACHIEVEMENT'",
                Long.class, user.getId());
    }

    private long achievementNotificationCount(UserEntity user, String name) {
        users.flush();
        return jdbc.queryForObject("""
                SELECT COUNT(*) FROM notifications
                WHERE recipient_user_id = ? AND message LIKE ?
                """, Long.class, user.getId(), "Achievement completed: " + name + " (%");
    }

    private List<String> achievementReferences(UserEntity user) {
        return jdbc.queryForList(
                "SELECT reference_id FROM point_transactions WHERE user_id = ? AND transaction_type = 'ACHIEVEMENT'",
                String.class, user.getId());
    }

    private void assertRefunded(UUID gameId) {
        assertThat(jdbc.queryForList("""
                SELECT amount FROM point_transactions
                WHERE transaction_type = 'WAGER_REFUND' AND reference_id = ? ORDER BY user_id
                """, Long.class, gameId.toString())).containsExactly(100L, 100L);
        assertThat(jdbc.queryForMap("SELECT status, rake_points FROM point_wagers WHERE game_id = ?", gameId))
                .containsEntry("status", "REFUNDED")
                .containsEntry("rake_points", 0L);
        assertThat(points.wagerPayouts(gameId)).isEmpty();
        assertThat(points.wagerDeltas(gameId)).hasSize(2).allSatisfy((userId, delta) ->
                assertThat(delta).isZero());
    }

    private UserEntity user(String prefix) {
        var suffix = UUID.randomUUID().toString().substring(0, 8);
        var user = users.saveAndFlush(new UserEntity(prefix + '-' + suffix + "@example.com", prefix + '-' + suffix));
        points.initialize(user);
        return user;
    }
}
