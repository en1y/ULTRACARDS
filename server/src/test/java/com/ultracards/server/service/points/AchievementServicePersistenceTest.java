package com.ultracards.server.service.points;

import com.ultracards.gateway.dto.admin.AdminAchievementDTO;
import com.ultracards.gateway.dto.admin.AdminStreakPatchDTO;
import com.ultracards.server.entity.UserEntity;
import com.ultracards.server.repositories.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.ZoneId;
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
class AchievementServicePersistenceTest {
    private static final ZoneId POINTS_ZONE = ZoneId.of("Europe/Zagreb");

    @Autowired private AchievementService achievements;
    @Autowired private PointsService points;
    @Autowired private UserRepository users;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void seedsTheRequestedAchievementMilestones() {
        assertThat(jdbc.queryForList("""
                SELECT code FROM point_achievements
                WHERE code IN ('STREAK_50', 'GAMES_250', 'GAMES_750')
                ORDER BY code
                """, String.class)).containsExactly("GAMES_250", "GAMES_750", "STREAK_50");
    }

    @Test
    void countsConsecutiveDaysAndResetsAfterAnUncoveredGap() {
        var user = user("streak");

        assertThat(achievements.recordPlay(user.getId())).isEqualTo(1);
        // A second game the same day is not a second day.
        assertThat(achievements.recordPlay(user.getId())).isEqualTo(1);

        playedOn(user, today().minusDays(1));
        assertThat(achievements.recordPlay(user.getId())).isEqualTo(2);

        // Two missed days with no freezes held: the streak starts over.
        playedOn(user, today().minusDays(3), 9, 0);
        assertThat(achievements.recordPlay(user.getId())).isEqualTo(1);
        assertThat(achievements.streak(user.getId()).longest()).isEqualTo(9);
    }

    @Test
    void earnsAFreezeEveryWeekAndCapsAtThree() {
        var user = user("freeze-earn");

        playedOn(user, today().minusDays(1), 6, 0);
        assertThat(achievements.recordPlay(user.getId())).isEqualTo(7);
        assertThat(achievements.streak(user.getId()).freezes()).isEqualTo(1);

        // Day 8 is inside the week already paid for, so it must not pay a second freeze.
        playedOn(user, today().minusDays(1), 7, 7);
        jdbc.update("UPDATE user_streaks SET streak_freezes = 1 WHERE user_id = ?", user.getId());
        achievements.recordPlay(user.getId());
        assertThat(achievements.streak(user.getId()).freezes()).isEqualTo(1);

        // A streak long past three weeks still tops out at the cap.
        jdbc.update("UPDATE user_streaks SET current_streak = 27, freezes_paid_through = 21, streak_freezes = 3,"
                + " last_played_date = ? WHERE user_id = ?", today().minusDays(1), user.getId());
        achievements.recordPlay(user.getId());
        assertThat(achievements.streak(user.getId()).freezes()).isEqualTo(AchievementService.MAX_FREEZES);
    }

    @Test
    void spendsFreezesToBridgeMissedDaysAndCountsThemTowardTheStreak() {
        var user = user("freeze-spend");
        // Played three days ago with two freezes in hand: two days to bridge.
        playedOn(user, today().minusDays(3), 10, 7);
        jdbc.update("UPDATE user_streaks SET streak_freezes = 2 WHERE user_id = ?", user.getId());

        assertThat(achievements.streak(user.getId()).current()).isEqualTo(10);
        assertThat(achievements.streak(user.getId()).atRisk()).isTrue();
        assertThat(achievements.streak(user.getId()).freezesToKeep()).isEqualTo(2);

        assertThat(achievements.recordPlay(user.getId())).isEqualTo(13);
        var after = achievements.streak(user.getId());
        assertThat(after.freezes()).isZero();
        assertThat(after.atRisk()).isFalse();
        assertThat(after.playedToday()).isTrue();
    }

    @Test
    void reportsAStreakBrokenBeyondWhatItsFreezesCover() {
        var user = user("freeze-short");
        playedOn(user, today().minusDays(4), 10, 7);
        jdbc.update("UPDATE user_streaks SET streak_freezes = 1 WHERE user_id = ?", user.getId());

        // Three missed days against one freeze: the streak reads as gone before the next play.
        assertThat(achievements.streak(user.getId()).current()).isZero();
        assertThat(achievements.recordPlay(user.getId())).isEqualTo(1);
    }

    @Test
    void paysEachAchievementOnceAndKeepsItAfterTheAdminDeletesIt() {
        var user = user("milestone");
        replaceWith(new AdminAchievementDTO("TEST_STREAK_1", "Play a day", "STREAK", 1L, 750L, true));

        achievements.recordGame(reload(user));
        var balance = points.balance(reload(user));
        assertThat(earned(user, "TEST_STREAK_1")).isEqualTo(1);

        // A second game must not pay the same milestone again.
        achievements.recordGame(reload(user));
        assertThat(points.balance(reload(user))).isEqualTo(balance);
        assertThat(earned(user, "TEST_STREAK_1")).isEqualTo(1);

        // Dropping it from the admin list leaves the earned row, so re-adding never re-pays.
        replaceWith(new AdminAchievementDTO("TEST_STREAK_1", "Play a day", "STREAK", 1L, 750L, true));
        achievements.recordGame(reload(user));
        assertThat(points.balance(reload(user))).isEqualTo(balance);
    }

    @Test
    void reportsProgressTowardLifetimeAchievements() {
        var user = user("progress");
        replaceWith(new AdminAchievementDTO("TEST_GAMES_5", "Finish 5 games", "GAMES", 5L, 100L, true));

        var summary = achievements.summary(reload(user));
        assertThat(summary.achievements()).singleElement().satisfies(achievement -> {
            assertThat(achievement.metric()).isEqualTo("GAMES");
            assertThat(achievement.target()).isEqualTo(5);
            assertThat(achievement.earned()).isFalse();
        });
    }

    /** Viewing someone else must not pay them, but must still show what they have reached. */
    @Test
    void showsAnotherPlayerAsEarnedWithoutGrantingThemAnything() {
        var user = user("public-view");
        replaceWith(new AdminAchievementDTO("TEST_STREAK_1", "Play a day", "STREAK", 1L, 750L, true));
        achievements.recordPlay(user.getId());
        var balance = points.balance(reload(user));

        assertThat(achievements.publicSummary(user.getId()).achievements())
                .singleElement()
                .satisfies(achievement -> assertThat(achievement.earned()).isTrue());
        assertThat(points.balance(reload(user))).isEqualTo(balance);
        assertThat(earned(user, "TEST_STREAK_1")).isZero();
    }

    @Test
    void listsTheHoldersOfAnAchievement() {
        var user = user("holder");
        replaceWith(new AdminAchievementDTO("TEST_STREAK_1", "Play a day", "STREAK", 1L, 750L, true));
        achievements.recordGame(reload(user));

        var holders = achievements.holders("TEST_STREAK_1", 0, 25);
        assertThat(holders.items()).anySatisfy(holder -> {
            assertThat(holder.userId()).isEqualTo(user.getId());
            assertThat(holder.username()).isEqualTo(user.getUsername());
            assertThat(holder.rewardPoints()).isEqualTo(750);
            assertThat(holder.earnedAt()).isNotNull();
        });
        assertThat(achievements.holders("NOBODY_HAS_THIS", 0, 25).items()).isEmpty();
    }

    @Test
    void correctsAStreakFieldByFieldAndLeavesTheRestAlone() {
        var user = user("correction");
        playedOn(user, today().minusDays(1), 4, 0);

        // Only the freeze count is supplied, so the streak itself must survive untouched.
        var freezesOnly = achievements.updateStreak(user.getId(),
                new AdminStreakPatchDTO(null, null, null, 2, "test"));
        assertThat(freezesOnly.current()).isEqualTo(4);
        assertThat(freezesOnly.freezes()).isEqualTo(2);

        // A corrected run above the old record becomes the new record.
        var raised = achievements.updateStreak(user.getId(),
                new AdminStreakPatchDTO(30, null, today(), null, "test"));
        assertThat(raised.current()).isEqualTo(30);
        assertThat(raised.longest()).isEqualTo(30);
        assertThat(raised.playedToday()).isTrue();
    }

    @Test
    void resetsAStreakBackToNothing() {
        var user = user("wipe");
        playedOn(user, today(), 12, 7);
        achievements.updateStreak(user.getId(), new AdminStreakPatchDTO(null, null, null, 3, "test"));

        var cleared = achievements.resetStreak(user.getId());
        assertThat(cleared.current()).isZero();
        assertThat(cleared.longest()).isZero();
        assertThat(cleared.freezes()).isZero();
        assertThat(cleared.lastPlayedDate()).isNull();
    }

    @Test
    void rejectsAFutureLastPlayedDate() {
        var user = user("future");
        assertThatThrownBy(() -> achievements.updateStreak(user.getId(),
                new AdminStreakPatchDTO(null, null, today().plusDays(1), null, "test")))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void rejectsUnknownMetricsAndOutOfRangeFreezes() {
        var user = user("validation");
        assertThatThrownBy(() -> achievements.replaceAchievements(
                List.of(new AdminAchievementDTO(null, "Bad", "LOSSES", 3L, 0L, true))))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> achievements.updateStreak(user.getId(),
                new AdminStreakPatchDTO(null, null, null, 4, "test")))
                .isInstanceOf(ResponseStatusException.class);
    }

    private static LocalDate today() {
        return LocalDate.now(POINTS_ZONE);
    }

    /** Seeds the stored streak as if the player last played on {@code date}. */
    private void playedOn(UserEntity user, LocalDate date) {
        playedOn(user, date, 1, 0);
    }

    private void playedOn(UserEntity user, LocalDate date, int current, int paidThrough) {
        jdbc.update("""
                INSERT INTO user_streaks(user_id, current_streak, longest_streak, last_played_date,
                                         streak_freezes, freezes_paid_through)
                VALUES (?, ?, ?, ?, 0, ?)
                ON CONFLICT (user_id) DO UPDATE SET
                    current_streak = EXCLUDED.current_streak,
                    longest_streak = GREATEST(user_streaks.longest_streak, EXCLUDED.current_streak),
                    last_played_date = EXCLUDED.last_played_date,
                    streak_freezes = EXCLUDED.streak_freezes,
                    freezes_paid_through = EXCLUDED.freezes_paid_through
                """, user.getId(), current, current, date, paidThrough);
    }

    private void replaceWith(AdminAchievementDTO achievement) {
        achievements.replaceAchievements(List.of(achievement));
    }

    private long earned(UserEntity user, String code) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM user_achievements WHERE user_id = ? AND code = ?",
                Long.class, user.getId(), code);
    }

    private UserEntity reload(UserEntity user) {
        return users.findById(user.getId()).orElseThrow();
    }

    private UserEntity user(String prefix) {
        var suffix = UUID.randomUUID().toString().substring(0, 8);
        var user = users.saveAndFlush(new UserEntity(prefix + '-' + suffix + "@example.com", prefix + '-' + suffix));
        points.initialize(user);
        return user;
    }
}
