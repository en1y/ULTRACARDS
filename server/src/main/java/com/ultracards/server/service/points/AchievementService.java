package com.ultracards.server.service.points;

import com.ultracards.gateway.dto.admin.AdminAchievementDTO;
import com.ultracards.gateway.dto.admin.AdminAchievementHolderDTO;
import com.ultracards.gateway.dto.admin.AdminPageDTO;
import com.ultracards.gateway.dto.admin.AdminStreakPatchDTO;
import com.ultracards.gateway.dto.points.PointsAchievementDTO;
import com.ultracards.gateway.dto.points.PointsAchievementsDTO;
import com.ultracards.gateway.dto.points.PointsActivityDayDTO;
import com.ultracards.gateway.dto.points.PointsStreakDTO;
import com.ultracards.server.entity.UserEntity;
import com.ultracards.server.enums.games.GameType;
import com.ultracards.server.repositories.UserRepository;
import com.ultracards.server.service.games.UserGamesStatsService;
import com.ultracards.server.service.notifications.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Daily play streaks, streak freezes, and the one-time achievements they feed.
 *
 * <p>The streak is stored in {@code user_streaks} rather than recomputed: V44 seeded it
 * from {@code point_game_results}, and every finished game moves it forward from there.
 */
@Service
@RequiredArgsConstructor
public class AchievementService {
    /** What an achievement can count; mirrors the point_achievements metric check. */
    private static final List<String> METRICS = List.of("STREAK", "GAMES", "WINS");
    public static final int MAX_FREEZES = 3;
    /** A freeze is earned for every unbroken week. */
    private static final int DAYS_PER_FREEZE = 7;
    /** 53 whole weeks, plus the padding that lands the graph's first column on a Monday. */
    private static final int MAX_ACTIVITY_DAYS = 378;

    private final JdbcTemplate jdbc;
    private final PointsService points;
    private final UserRepository users;
    private final UserGamesStatsService gameStats;
    private final NotificationService notifications;

    private static LocalDate today() {
        return LocalDate.now(PointsService.POINTS_ZONE);
    }

    // ---------------------------------------------------------------- player reads

    /**
     * Achievements settle on read as well as on game end, the same way daily goals do:
     * a player who passed a milestone before this shipped would otherwise sit at full
     * progress forever without collecting.
     */
    @Transactional
    public PointsAchievementsDTO summary(UserEntity user) {
        var current = users.findByIdForUpdate(user.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        award(current);
        return new PointsAchievementsDTO(streak(current.getId()), achievements(current.getId()));
    }

    /** Another player's tab: their progress, without settling anything on their behalf. */
    @Transactional(readOnly = true)
    public PointsAchievementsDTO publicSummary(Long userId) {
        if (!users.existsById(userId))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
        return new PointsAchievementsDTO(streak(userId), achievements(userId));
    }

    @Transactional(readOnly = true)
    public PointsStreakDTO streak(Long userId) {
        var row = streakRow(userId);
        var today = today();
        var last = row.lastPlayedDate();
        if (last == null) return new PointsStreakDTO(0, row.longest(), row.freezes(), null, false, false, 0);

        var missed = (int) ChronoUnit.DAYS.between(last, today);
        var needed = Math.max(0, missed - 1);
        // Freezes are spent on the next play, not by a nightly job, so the streak the
        // player sees is the one that play would restore.
        var alive = needed <= row.freezes();
        return new PointsStreakDTO(alive ? row.current() : 0, row.longest(), row.freezes(), last,
                missed == 0, alive && missed > 0, alive ? needed : 0);
    }

    /**
     * One entry per day a player finished a game, oldest first. Days with no games are
     * left out; the graph fills the gaps so an idle year costs nothing to send.
     */
    @Transactional(readOnly = true)
    public List<PointsActivityDayDTO> activity(Long userId, int days) {
        var window = Math.clamp(days, 1, MAX_ACTIVITY_DAYS);
        var since = today().minusDays(window - 1L);
        return jdbc.query("""
                SELECT (ended_at AT TIME ZONE ?)::date AS played_on,
                       COUNT(*) AS games,
                       COUNT(*) FILTER (WHERE outcome = 'WIN') AS wins
                FROM point_game_results
                WHERE user_id = ? AND (ended_at AT TIME ZONE ?)::date >= ?
                GROUP BY played_on
                ORDER BY played_on
                """, (result, row) -> new PointsActivityDayDTO(result.getObject("played_on", LocalDate.class),
                        result.getLong("games"), result.getLong("wins")),
                PointsService.POINTS_ZONE.getId(), userId, PointsService.POINTS_ZONE.getId(), since);
    }

    // ---------------------------------------------------------------- streak writes

    /**
     * Moves the streak to {@code today} after a finished game, spending freezes to bridge
     * the days the player missed. Called once per player per game; a second game the same
     * day changes nothing.
     *
     * @return the streak length after this play
     */
    @Transactional
    public int recordPlay(Long userId) {
        var today = today();
        var row = streakRow(userId);
        var last = row.lastPlayedDate();
        if (today.equals(last)) return row.current();

        var current = row.current();
        var freezes = row.freezes();
        var paidThrough = row.freezesPaidThrough();
        if (last == null) {
            current = 1;
            paidThrough = 0;
        } else {
            var missed = (int) ChronoUnit.DAYS.between(last, today) - 1;
            if (missed <= 0) {
                current += 1;
            } else if (missed <= freezes) {
                // Each freeze covers one missed day, and the bridged days count toward
                // the streak: that is what makes a freeze worth having.
                freezes -= missed;
                current += missed + 1;
                notifications.createTextNotification(userId, missed == 1
                        ? "A streak freeze saved your streak."
                        : missed + " streak freezes saved your streak.");
            } else {
                current = 1;
                freezes = 0;
                paidThrough = 0;
            }
        }

        // One freeze per completed week, never more than MAX_FREEZES held at once.
        var earned = current / DAYS_PER_FREEZE - paidThrough / DAYS_PER_FREEZE;
        if (earned > 0) {
            var granted = Math.min(MAX_FREEZES - freezes, earned);
            if (granted > 0) {
                freezes += granted;
                notifications.createTextNotification(userId, granted == 1
                        ? "You earned a streak freeze."
                        : "You earned " + granted + " streak freezes.");
            }
            paidThrough = current - current % DAYS_PER_FREEZE;
        }

        var longest = Math.max(row.longest(), current);
        jdbc.update("""
                INSERT INTO user_streaks(user_id, current_streak, longest_streak, last_played_date,
                                         streak_freezes, freezes_paid_through)
                VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT (user_id) DO UPDATE SET
                    current_streak = EXCLUDED.current_streak,
                    longest_streak = EXCLUDED.longest_streak,
                    last_played_date = EXCLUDED.last_played_date,
                    streak_freezes = EXCLUDED.streak_freezes,
                    freezes_paid_through = EXCLUDED.freezes_paid_through
                """, userId, current, longest, today, freezes, paidThrough);
        return current;
    }

    // ---------------------------------------------------------------- awarding

    /** Pays every achievement the player now qualifies for. Safe to call repeatedly. */
    @Transactional
    public void award(UserEntity user) {
        var progress = progress(user);
        var earned = earnedCodes(user.getId());
        for (var achievement : achievementRows(true)) {
            if (earned.contains(achievement.code())) continue;
            if (progress.of(achievement.metric()) < achievement.target()) continue;
            // The primary key is what makes this once-only; a losing race inserts nothing
            // and must not pay out either.
            var inserted = jdbc.update("""
                    INSERT INTO user_achievements(user_id, code, earned_at, reward_points)
                    VALUES (?, ?, ?, ?)
                    ON CONFLICT DO NOTHING
                    """, user.getId(), achievement.code(), Timestamp.from(Instant.now()),
                    achievement.rewardPoints()) > 0;
            if (inserted) points.grantMilestone(user, achievement.rewardPoints(), achievement.code(),
                    achievement.name());
        }
    }

    /** Called as a game is recorded: advance the streak first, then pay what it unlocked. */
    @Transactional
    public void recordGame(UserEntity user) {
        recordPlay(user.getId());
        award(user);
    }

    // ---------------------------------------------------------------- admin

    @Transactional(readOnly = true)
    public List<AdminAchievementDTO> adminAchievements() {
        var result = new ArrayList<AdminAchievementDTO>();
        for (var row : achievementRows(false))
            result.add(new AdminAchievementDTO(row.code(), row.name(), row.metric(), row.target(),
                    row.rewardPoints(), row.enabled()));
        return result;
    }

    /**
     * The list replaces what is stored: achievements left out of it are dropped. Rows in
     * {@code user_achievements} survive a delete, so a re-added code never pays twice.
     */
    @Transactional
    public List<AdminAchievementDTO> replaceAchievements(List<AdminAchievementDTO> achievements) {
        if (achievements == null)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Achievements are required");
        jdbc.update("DELETE FROM point_achievements");
        var codes = new LinkedHashSet<String>();
        for (int index = 0; index < achievements.size(); index++) {
            var achievement = achievements.get(index);
            if (achievement == null || achievement.name() == null || achievement.name().isBlank())
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Every achievement needs a name");
            var metric = achievement.metric() == null ? "" : achievement.metric().trim().toUpperCase();
            if (!METRICS.contains(metric))
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Unknown achievement metric: " + achievement.metric());
            if (achievement.target() == null || achievement.target() <= 0)
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Achievement target must be above 0");
            var reward = achievement.rewardPoints() == null ? 0 : achievement.rewardPoints();
            if (reward < 0)
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Achievement reward cannot be negative");
            // A new achievement gets its own code so it never inherits a deleted one's grants.
            var code = achievement.code() == null || achievement.code().isBlank()
                    ? UUID.randomUUID().toString() : achievement.code().trim();
            if (!codes.add(code))
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Duplicate achievement");
            jdbc.update("""
                    INSERT INTO point_achievements(code, name, metric, target, reward_points, enabled, sort_order)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                    """, code, achievement.name().trim(), metric, achievement.target(), reward,
                    achievement.enabled() == null || achievement.enabled(), index);
        }
        return adminAchievements();
    }

    /** Everyone who earned one achievement, newest first, for the admin holder list. */
    @Transactional(readOnly = true)
    public AdminPageDTO<AdminAchievementHolderDTO> holders(String code, int page, int size) {
        var pageNumber = Math.max(0, page);
        var pageSize = Math.clamp(size, 1, 100);
        var total = scalar("SELECT COUNT(*) FROM user_achievements WHERE code = ?", code);
        var items = jdbc.query("""
                SELECT earned.user_id, player.username, player.email, earned.earned_at, earned.reward_points
                FROM user_achievements earned
                JOIN users player ON player.id = earned.user_id
                WHERE earned.code = ?
                ORDER BY earned.earned_at DESC, earned.user_id
                LIMIT ? OFFSET ?
                """, (result, row) -> new AdminAchievementHolderDTO(result.getLong("user_id"),
                        result.getString("username"), result.getString("email"),
                        result.getTimestamp("earned_at").toInstant(), result.getLong("reward_points")),
                code, pageSize, (long) pageNumber * pageSize);
        var totalPages = (int) Math.ceil((double) total / pageSize);
        return new AdminPageDTO<>(items, pageNumber, pageSize, total, totalPages);
    }

    /**
     * An audited streak correction: for the support cases where an outage or a bad
     * finalisation cost someone a streak they had actually earned. Null fields keep
     * whatever the player already has.
     */
    @Transactional
    public PointsStreakDTO updateStreak(Long userId, AdminStreakPatchDTO patch) {
        if (!users.existsById(userId))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found: " + userId);
        if (patch == null) return streak(userId);

        var row = streakRow(userId);
        var freezes = patch.freezes() == null ? row.freezes() : patch.freezes();
        if (freezes < 0 || freezes > MAX_FREEZES)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Streak freezes must be between 0 and " + MAX_FREEZES);

        var current = patch.currentStreak() == null ? row.current() : patch.currentStreak();
        if (current < 0)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Current streak cannot be negative");

        var longest = patch.longestStreak() == null ? row.longest() : patch.longestStreak();
        if (longest < 0)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Longest streak cannot be negative");
        // The longest streak is a high-water mark; a current run above it is the new one.
        longest = Math.max(longest, current);

        var lastPlayed = patch.lastPlayedDate() == null ? row.lastPlayedDate() : patch.lastPlayedDate();
        if (lastPlayed != null && lastPlayed.isAfter(today()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Last played date cannot be in the future");
        // A streak with no day attached would read as alive forever, so anchor it to today.
        if (current > 0 && lastPlayed == null) lastPlayed = today();

        jdbc.update("""
                INSERT INTO user_streaks(user_id, current_streak, longest_streak, last_played_date,
                                         streak_freezes, freezes_paid_through)
                VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT (user_id) DO UPDATE SET
                    current_streak = EXCLUDED.current_streak,
                    longest_streak = EXCLUDED.longest_streak,
                    last_played_date = EXCLUDED.last_played_date,
                    streak_freezes = EXCLUDED.streak_freezes,
                    freezes_paid_through = EXCLUDED.freezes_paid_through
                """, userId, current, longest, lastPlayed, freezes,
                // Re-anchor the freeze ledger to the corrected streak, or a shortened one
                // would owe freezes for weeks it no longer covers.
                Math.min(row.freezesPaidThrough(), current - current % DAYS_PER_FREEZE));
        return streak(userId);
    }

    /** Wipes a streak back to nothing, freezes included. */
    @Transactional
    public PointsStreakDTO resetStreak(Long userId) {
        if (!users.existsById(userId))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found: " + userId);
        jdbc.update("DELETE FROM user_streaks WHERE user_id = ?", userId);
        return streak(userId);
    }

    private long scalar(String sql, Object... args) {
        var value = jdbc.queryForObject(sql, Long.class, args);
        return value == null ? 0 : value;
    }

    // ---------------------------------------------------------------- internals

    private List<PointsAchievementDTO> achievements(Long userId) {
        var progress = progress(userId);
        var earned = earnedCodes(userId);
        var result = new ArrayList<PointsAchievementDTO>();
        for (var row : achievementRows(true)) {
            var current = progress.of(row.metric());
            // Reaching the target counts as earned even before the grant lands. Looking at
            // someone else's profile is read-only and must not pay them, and a milestone an
            // admin later raised the target on stays earned because the row outlives it.
            var done = earned.contains(row.code()) || current >= row.target();
            result.add(new PointsAchievementDTO(row.code(), row.name(), row.metric(), row.rewardPoints(),
                    Math.min(current, row.target()), row.target(), done));
        }
        return result;
    }

    private Progress progress(UserEntity user) {
        return new Progress(streak(user.getId()).current(), lifetime(user, false), lifetime(user, true));
    }

    private Progress progress(Long userId) {
        var user = users.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        return progress(user);
    }

    /** Lifetime totals come from the stats the profile already shows, not the Points ledger. */
    private long lifetime(UserEntity user, boolean winsOnly) {
        var stats = gameStats.getByUser(user);
        if (stats == null) return 0;
        var total = 0L;
        for (var gameType : GameType.values())
            total += winsOnly ? stats.getGamesWon(gameType) : stats.getGamesPlayed(gameType);
        return total;
    }

    private Set<String> earnedCodes(Long userId) {
        var codes = new HashSet<String>();
        jdbc.query("SELECT code FROM user_achievements WHERE user_id = ?",
                result -> { codes.add(result.getString(1)); }, userId);
        return codes;
    }

    private List<AchievementRow> achievementRows(boolean enabledOnly) {
        return jdbc.query("""
                SELECT code, name, metric, target, reward_points, enabled
                FROM point_achievements%s
                ORDER BY sort_order, code
                """.formatted(enabledOnly ? " WHERE enabled" : ""), (result, row) -> new AchievementRow(
                result.getString("code"), result.getString("name"), result.getString("metric"),
                result.getLong("target"), result.getLong("reward_points"), result.getBoolean("enabled")));
    }

    private StreakRow streakRow(Long userId) {
        var rows = jdbc.query("""
                SELECT current_streak, longest_streak, last_played_date, streak_freezes, freezes_paid_through
                FROM user_streaks WHERE user_id = ?
                """, (result, row) -> new StreakRow(result.getInt("current_streak"),
                        result.getInt("longest_streak"),
                        result.getObject("last_played_date", LocalDate.class),
                        result.getInt("streak_freezes"), result.getInt("freezes_paid_through")), userId);
        return rows.isEmpty() ? new StreakRow(0, 0, null, 0, 0) : rows.getFirst();
    }

    private record StreakRow(int current, int longest, LocalDate lastPlayedDate, int freezes,
                             int freezesPaidThrough) {
    }

    private record AchievementRow(String code, String name, String metric, long target, long rewardPoints,
                                  boolean enabled) {
    }

    private record Progress(long streak, long games, long wins) {
        long of(String metric) {
            return switch (metric) {
                case "GAMES" -> games;
                case "WINS" -> wins;
                default -> streak;
            };
        }
    }
}
