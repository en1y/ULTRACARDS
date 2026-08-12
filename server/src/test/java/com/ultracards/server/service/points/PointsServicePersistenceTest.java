package com.ultracards.server.service.points;

import com.ultracards.gateway.dto.games.lobby.WagerConfigDTO;
import com.ultracards.gateway.dto.admin.AdminPointEventPatchDTO;
import com.ultracards.gateway.dto.points.PointsAchievementDTO;
import com.ultracards.server.entity.UserEntity;
import com.ultracards.server.enums.games.GameType;
import com.ultracards.server.repositories.UserRepository;
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
    void scheduledEventCountsWinsLossesAndDrawsAndAwardsOnlyOnce() {
        var user = user("event-player");
        var opponent = user("event-opponent");
        var third = user("event-third");
        var goal = new AdminPointEventPatchDTO.Achievement(null, "Durak set", "Do all three",
                3, 1, 1, 1, 200L);
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
                """, Long.class, user.getId())).isEqualTo(2L);
        assertThat(jdbc.queryForObject("""
                SELECT COALESCE(SUM(amount), 0) FROM point_transactions
                WHERE user_id = ? AND transaction_type IN ('EVENT_ACHIEVEMENT', 'EVENT_COMPLETION')
                """, Long.class, user.getId())).isEqualTo(500L);
    }

    @Test
    void upcomingEventWithoutAchievementsIsAnnouncedButCannotAutoPay() {
        var user = user("upcoming-player");
        var event = points.createEvent(new AdminPointEventPatchDTO("Tomorrow", "Notice only",
                Instant.now().plusSeconds(3_600), null, List.of(), 500L, true, List.of(), "test"));

        var shown = points.events(user).stream().filter(item -> item.id().equals(event.id())).findFirst().orElseThrow();

        assertThat(shown.active()).isFalse();
        assertThat(shown.achievements()).isEmpty();
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM point_transactions
                WHERE user_id = ? AND transaction_type LIKE 'EVENT_%'
                """, Long.class, user.getId())).isZero();
    }

    @Test
    void preservesAchievementIdentityWhenAnAdminClientUpdatesGoalsByPosition() {
        var goal = new AdminPointEventPatchDTO.Achievement(null, "Original", "", 1, 0, 0, 0, 10L);
        var patch = new AdminPointEventPatchDTO("Editable", "", Instant.now().minusSeconds(60), null,
                List.of(), 0L, true, List.of(goal), "test");
        var event = points.createEvent(patch);
        var originalId = event.achievements().getFirst().id();

        var renamed = new AdminPointEventPatchDTO.Achievement(null, "Renamed", "", 2, 0, 0, 0, 20L);
        var updated = points.updateEvent(event.id(), new AdminPointEventPatchDTO("Editable", "",
                patch.startsAt(), null, List.of(), 0L, true, List.of(renamed), "test"));

        assertThat(updated.achievements().getFirst().id()).isEqualTo(originalId);
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
        assertThat(achievementReferences(user)).allMatch(reference -> reference.endsWith(':' + today()));
        // Re-reading the page must not pay the same day's goals twice.
        var balance = account.balance();
        assertThat(points.summary(user).balance()).isEqualTo(balance);
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
