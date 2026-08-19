package com.ultracards.server.service.points;

import com.ultracards.gateway.dto.admin.AdminDailyGoalDTO;
import com.ultracards.gateway.dto.admin.AdminPointEventDTO;
import com.ultracards.gateway.dto.admin.AdminPointEventPatchDTO;
import com.ultracards.gateway.dto.admin.AdminEconomyDashboardDTO;
import com.ultracards.gateway.dto.games.lobby.WagerConfigDTO;
import com.ultracards.gateway.dto.points.PointEventDTO;
import com.ultracards.gateway.dto.points.PointTransactionDTO;
import com.ultracards.gateway.dto.points.PointTransactionPageDTO;
import com.ultracards.gateway.dto.points.PointsAccountDTO;
import com.ultracards.gateway.dto.points.PointsAchievementDTO;
import com.ultracards.gateway.dto.points.PointsClaimDTO;
import com.ultracards.gateway.dto.points.PointsSeriesPointDTO;
import com.ultracards.gateway.dto.points.PointsSettingsDTO;
import com.ultracards.server.entity.UserEntity;
import com.ultracards.server.enums.NotificationType;
import com.ultracards.server.enums.games.GameType;
import com.ultracards.server.repositories.UserRepository;
import com.ultracards.server.service.games.GameAvailabilityService;
import com.ultracards.server.service.notifications.NotificationService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.boot.context.event.ApplicationReadyEvent;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class PointsService {
    public static final long MIN_STAKE = 100;
    public static final long MAX_STAKE = 1_000_000;
    public static final int DEFAULT_WAGER_FEE_PERCENT = 4;
    /** Fee override row that covers every mode of a game. */
    private static final String ALL_MODES = "*";
    /** What a daily goal can count; mirrors the point_daily_goals metric check. */
    private static final List<String> DAILY_METRICS = List.of("GAMES", "WINS", "LOSSES", "DRAWS");
    /** The day boundary the whole economy uses; {@link AchievementService} counts streak days by it too. */
    static final ZoneId POINTS_ZONE = ZoneId.of("Europe/Zagreb");

    private final UserRepository users;
    private final JdbcTemplate jdbc;
    private final NotificationService notifications;
    private final GameAvailabilityService gameAvailability;
    private final MarkdownRenderer markdown;
    /** Lazy: achievements need this service to pay out, so the two reference each other. */
    private final AchievementService achievements;

    @Value("${app.points.basic-user-amount:1500}")
    private long basicUserPoints;

    public PointsService(UserRepository users, JdbcTemplate jdbc, NotificationService notifications,
                         GameAvailabilityService gameAvailability, MarkdownRenderer markdown,
                         @Lazy AchievementService achievements) {
        this.users = users;
        this.jdbc = jdbc;
        this.notifications = notifications;
        this.gameAvailability = gameAvailability;
        this.markdown = markdown;
        this.achievements = achievements;
    }

    @Transactional
    public void initialize(UserEntity user) {
        if (transactionExists(user.getId(), Type.INITIAL_GRANT, "INITIAL")) return;
        // ponytail: a 0 starting balance leaves no grant row to mark the account as
        // initialized. Only user creation calls this, so nothing runs it twice.
        var starting = startingBalance();
        user.setPointsBalance(starting);
        users.save(user);
        if (starting > 0) insert(user.getId(), starting, starting, Type.INITIAL_GRANT, "INITIAL");
    }

    /**
     * Achievements used to be granted only as a game ended, so anyone who passed a
     * milestone before the economy shipped — or between two sessions — sat at full
     * progress and never collected. Settling up on read keeps the page honest;
     * {@link #applyOnce} makes the repeat visits free.
     */
    @Transactional
    public PointsAccountDTO summary(UserEntity user) {
        var current = users.findByIdForUpdate(user.getId()).orElseThrow(() -> notFound(user.getId()));
        awardAchievements(current);
        awardEventAchievements(current, null, null);
        return buildSummary(current);
    }

    @Transactional(readOnly = true)
    public long balance(UserEntity user) {
        return users.findById(user.getId()).orElseThrow(() -> notFound(user.getId())).getPointsBalance();
    }

    /** Balances for a set of accounts — what a lobby needs to size its bet. */
    @Transactional(readOnly = true)
    public Map<Long, Long> balances(Collection<Long> userIds) {
        if (userIds.isEmpty()) return Map.of();
        var ids = new ArrayList<>(new LinkedHashSet<>(userIds));
        var placeholders = String.join(",", java.util.Collections.nCopies(ids.size(), "?"));
        return jdbc.query("SELECT id, points_balance FROM users WHERE id IN (%s)".formatted(placeholders),
                result -> {
                    var balances = new HashMap<Long, Long>();
                    while (result.next()) balances.put(result.getLong(1), result.getLong(2));
                    return balances;
                }, ids.toArray());
    }

    @Transactional(readOnly = true)
    public Map<Long, Long> wagerPayouts(UUID gameId) {
        return jdbc.query("""
                SELECT p.user_id, p.payout_points
                FROM point_wager_players p
                JOIN point_wagers w ON w.game_id = p.game_id
                WHERE p.game_id = ? AND w.status = 'SETTLED' AND p.payout_points > p.stake_points
                ORDER BY p.user_id
                """, result -> {
            var payouts = new LinkedHashMap<Long, Long>();
            while (result.next()) payouts.put(result.getLong(1), result.getLong(2));
            return payouts;
        }, gameId);
    }

    /** Net wager result per participant: stake out plus payout or refund back. */
    @Transactional(readOnly = true)
    public Map<Long, Long> wagerDeltas(UUID gameId) {
        return jdbc.query("""
                SELECT user_id, COALESCE(SUM(amount), 0) AS delta
                FROM point_transactions
                WHERE reference_id = ?
                  AND transaction_type IN ('WAGER_STAKE', 'WAGER_PAYOUT', 'WAGER_REFUND')
                GROUP BY user_id
                ORDER BY user_id
                """, result -> {
            var deltas = new LinkedHashMap<Long, Long>();
            while (result.next()) deltas.put(result.getLong("user_id"), result.getLong("delta"));
            return deltas;
        }, gameId.toString());
    }

    @Transactional
    public PointsClaimDTO claimDaily(UserEntity principal) {
        var user = users.findByIdForUpdate(principal.getId()).orElseThrow(() -> notFound(principal.getId()));
        var today = LocalDate.now(POINTS_ZONE);
        var settings = settingsRow();
        if (user.getPointsBalance() >= settings.startingBalance())
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Daily Points are available below " + settings.startingBalance() + "P");
        if (today.equals(user.getLastPointsClaimDate()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Daily Points have already been claimed today");

        var reference = today.toString();
        if (transactionExists(user.getId(), Type.DAILY_CLAIM, reference))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Daily Points have already been claimed today");
        apply(user, settings.dailyReward(), Type.DAILY_CLAIM, reference);
        user.setLastPointsClaimDate(today);
        return new PointsClaimDTO(settings.dailyReward(), buildSummary(user));
    }

    @Transactional(readOnly = true)
    public PointTransactionPageDTO transactions(UserEntity user, int page, int size) {
        return transactions(user.getId(), page, size);
    }

    @Transactional(readOnly = true)
    public PointTransactionPageDTO transactions(Long userId, int page, int size) {
        var safePage = Math.max(0, page);
        var safeSize = Math.max(1, Math.min(100, size));
        var total = jdbc.queryForObject("SELECT COUNT(*) FROM point_transactions WHERE user_id = ?",
                Long.class, userId);
        var items = jdbc.query("""
                        SELECT id, amount, balance_after, transaction_type, reference_id, created_at
                        FROM point_transactions
                        WHERE user_id = ?
                        ORDER BY created_at DESC, id DESC
                        LIMIT ? OFFSET ?
                        """, (result, row) -> new PointTransactionDTO(
                        result.getObject("id", UUID.class), result.getLong("amount"),
                        result.getLong("balance_after"), result.getString("transaction_type"),
                        result.getString("reference_id"), result.getTimestamp("created_at").toInstant()),
                userId, safeSize, (long) safePage * safeSize);
        var count = total == null ? 0 : total;
        var pages = count == 0 ? 0 : (int) ((count + safeSize - 1) / safeSize);
        return new PointTransactionPageDTO(items, safePage, safeSize, count, pages);
    }

    /**
     * Balance over time for the chart. `days` of 0 means the whole ledger; the
     * stored balance-after is what gets plotted, so no running total is needed here.
     */
    @Transactional(readOnly = true)
    public List<PointsSeriesPointDTO> series(Long userId, int days) {
        var sql = """
                SELECT created_at, balance_after, amount, transaction_type
                FROM point_transactions
                WHERE user_id = ?%s
                ORDER BY created_at, id
                """.formatted(days > 0 ? " AND created_at >= ?" : "");
        Object[] args = days > 0
                ? new Object[]{userId, Timestamp.from(Instant.now().minusSeconds((long) days * 86_400L))}
                : new Object[]{userId};
        return jdbc.query(sql, (result, row) -> new PointsSeriesPointDTO(
                result.getTimestamp("created_at").toInstant(), result.getLong("balance_after"),
                result.getLong("amount"), result.getString("transaction_type")), args);
    }

    /**
     * What each game's bet did to a user's balance: stake out, payout or refund back.
     * Achievements and the daily claim are their own thing and stay out of it.
     */
    @Transactional(readOnly = true)
    public Map<UUID, Long> gameDeltas(Long userId, Collection<UUID> gameIds) {
        if (gameIds.isEmpty()) return Map.of();
        var references = new ArrayList<String>(gameIds.size());
        for (var gameId : gameIds) references.add(gameId.toString());
        var placeholders = String.join(",", java.util.Collections.nCopies(references.size(), "?"));
        var args = new Object[references.size() + 1];
        args[0] = userId;
        for (int i = 0; i < references.size(); i++) args[i + 1] = references.get(i);
        return jdbc.query("""
                SELECT reference_id, COALESCE(SUM(amount), 0) AS delta
                FROM point_transactions
                WHERE user_id = ? AND reference_id IN (%s)
                  AND transaction_type IN ('WAGER_STAKE', 'WAGER_PAYOUT', 'WAGER_REFUND')
                GROUP BY reference_id
                """.formatted(placeholders), result -> {
            var deltas = new HashMap<UUID, Long>();
            while (result.next()) deltas.put(UUID.fromString(result.getString("reference_id")), result.getLong("delta"));
            return deltas;
        }, args);
    }

    @Transactional
    public void reserveWager(UUID gameId, UUID lobbyId, WagerConfigDTO wager, List<UserEntity> participants) {
        reserveWager(gameId, lobbyId, wager, participants, null, null);
    }

    @Transactional
    public void reserveWager(UUID gameId, UUID lobbyId, WagerConfigDTO wager, List<UserEntity> participants,
                             GameType gameType, String mode) {
        var config = normalize(wager);
        if (!config.isEnabled()) return;
        var locked = lock(userIds(participants));
        // Name the shortfall rather than the rule: the host needs to know who to wait
        // for and how far to drop the bet.
        var insufficient = new ArrayList<String>();
        var affordable = Long.MAX_VALUE;
        for (var user : locked.values()) {
            affordable = Math.min(affordable, user.getPointsBalance());
            if (user.getPointsBalance() < config.stakePoints())
                insufficient.add(user.getUsername() + " (" + user.getPointsBalance() + "P)");
        }
        if (!insufficient.isEmpty())
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    String.join(", ", insufficient) + " cannot cover the " + config.stakePoints()
                            + "P bet. The highest everyone can afford is " + affordable + "P.");

        jdbc.update("""
                INSERT INTO point_wagers(game_id, lobby_id, stake_points, fee_percent, status, created_at)
                VALUES (?, ?, ?, ?, 'OPEN', ?)
                """, gameId, lobbyId, config.stakePoints(), wagerFeePercent(gameType, mode), Timestamp.from(Instant.now()));
        for (var user : locked.values()) {
            jdbc.update("""
                    INSERT INTO point_wager_players(game_id, user_id, stake_points)
                    VALUES (?, ?, ?)
                    """, gameId, user.getId(), config.stakePoints());
            apply(user, -config.stakePoints(), Type.WAGER_STAKE, gameId.toString());
        }
    }

    /**
     * Finishing a game pays nothing by itself — only a bet moves Points. The result
     * is still recorded, because the daily goals count games, not transactions.
     */
    @Transactional
    public void completeGame(UUID gameId, List<UserEntity> participants, Set<Long> winnerIds) {
        completeGame(gameId, participants, winnerIds, null, null);
    }

    @Transactional
    public void completeGame(UUID gameId, List<UserEntity> participants, Set<Long> winnerIds, GameType gameType) {
        completeGame(gameId, participants, winnerIds, gameType, null);
    }

    @Transactional
    public void completeGame(UUID gameId, List<UserEntity> participants, Set<Long> winnerIds, GameType gameType,
                             String gameMode) {
        var locked = lock(userIds(participants));
        var endedAt = Timestamp.from(Instant.now());
        var participantWinnerCount = 0;
        for (var userId : locked.keySet()) {
            if (winnerIds.contains(userId)) participantWinnerCount++;
        }
        var draw = participantWinnerCount == 0 || participantWinnerCount == locked.size();
        for (var user : locked.values()) {
            var outcome = draw ? "DRAW" : winnerIds.contains(user.getId()) ? "WIN" : "LOSS";
            var recorded = jdbc.update("""
                    INSERT INTO point_game_results(game_id, user_id, won, ended_at, game_type, game_mode, outcome)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT DO NOTHING
                    """, gameId, user.getId(), "WIN".equals(outcome), endedAt,
                    gameType == null ? null : gameType.name(), gameMode, outcome) > 0;
            awardAchievements(user);
            awardEventAchievements(user, gameType, recorded ? gameId : null);
            // Only a newly recorded result moves the streak; a retried finalisation must not.
            if (recorded) achievements.recordGame(user);
        }
        settleWager(gameId, locked, winnerIds);
    }

    @Transactional
    public void cancelWager(UUID gameId) {
        refundWager(gameId);
    }

    @Transactional
    public BalanceAdjustment adjust(Long userId, long amount, String reference) {
        if (amount == 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Points adjustment cannot be zero");
        var user = users.findByIdForUpdate(userId).orElseThrow(() -> notFound(userId));
        var previous = user.getPointsBalance();
        try {
            apply(user, amount, Type.ADMIN_ADJUSTMENT, reference);
        } catch (ArithmeticException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Points adjustment is too large", ex);
        }
        return new BalanceAdjustment(previous, user.getPointsBalance());
    }

    @Transactional(readOnly = true)
    public PointsSettingsDTO settings() {
        return settings(null, null);
    }

    /** The fee a player would pay in this game and mode; both null gives the global default. */
    @Transactional(readOnly = true)
    public PointsSettingsDTO settings(GameType gameType, String mode) {
        var settings = settingsRow();
        return new PointsSettingsDTO(wagerFeePercent(gameType, mode), settings.startingBalance(),
                settings.dailyReward());
    }

    /** Null leaves a setting where it is, so a caller can change just one of them. */
    @Transactional
    public PointsSettingsDTO updateSettings(Integer feePercent, Long balance, Long reward, Long actorId) {
        var current = settingsRow();
        var percent = feePercent == null ? current.wagerFeePercent() : feePercent;
        var startingBalance = balance == null ? current.startingBalance() : balance;
        var dailyReward = reward == null ? current.dailyReward() : reward;
        if (percent < 0 || percent > 100)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bet fee must be between 0% and 100%");
        if (startingBalance < 0)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Starting balance cannot be negative");
        if (dailyReward <= 0)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Daily reward must be above 0P");
        jdbc.update("""
                UPDATE point_settings
                SET wager_fee_percent = ?, starting_balance = ?, daily_reward = ?, updated_at = ?, updated_by = ?
                WHERE id = 1
                """, percent, startingBalance, dailyReward, Timestamp.from(Instant.now()), actorId);
        return new PointsSettingsDTO(percent, startingBalance, dailyReward);
    }

    private long startingBalance() {
        return settingsRow().startingBalance();
    }

    private SettingsRow settingsRow() {
        return jdbc.queryForObject("""
                SELECT wager_fee_percent, starting_balance, daily_reward FROM point_settings WHERE id = 1
                """, (result, row) -> new SettingsRow(result.getInt(1), result.getLong(2), result.getLong(3)));
    }

    @Transactional(readOnly = true)
    public List<AdminPointEventDTO> adminEvents() {
        var result = new ArrayList<AdminPointEventDTO>();
        for (var event : eventRows(false)) result.add(adminEvent(event));
        return result;
    }

    /**
     * Everything live or announced, plus the finished events this player actually
     * showed up for — the page needs that tail to have a history to show.
     */
    @Transactional(readOnly = true)
    public List<PointEventDTO> events(UserEntity user) {
        var now = Instant.now();
        var earned = eventRewards(user.getId());
        var completions = eventCompletions(user.getId());
        var result = new ArrayList<PointEventDTO>();
        for (var event : eventRows(false)) {
            if (!event.enabled()) continue;
            var ended = event.endsAt() != null && !event.endsAt().isAfter(now);
            if (ended && eventProgress(user.getId(), event, List.of()).games() == 0) continue;
            var item = pointEvent(event, user.getId(), earned, completions);
            if (!item.achievements().isEmpty() || item.hiddenAchievementCount() > 0) result.add(item);
        }
        result.sort(Comparator.comparingInt((PointEventDTO event) -> eventGroup(event, now))
                .thenComparing(event -> eventSortAt(event, now), Comparator.reverseOrder()));
        return result;
    }

    private static int eventGroup(PointEventDTO event, Instant now) {
        if (event.active()) return 0;
        if (event.startsAt().isAfter(now)) return 1;
        return 2;
    }

    private static Instant eventSortAt(PointEventDTO event, Instant now) {
        if (event.completedAt() != null && event.endsAt() == null) return event.completedAt();
        if (event.endsAt() != null && !event.endsAt().isAfter(now)) return event.endsAt();
        return event.startsAt();
    }

    /** Points already paid out per event goal and per event completion, by reference id. */
    private Map<String, Long> eventRewards(Long userId) {
        return jdbc.query("""
                SELECT reference_id, COALESCE(SUM(amount), 0) AS total
                FROM point_transactions
                WHERE user_id = ? AND transaction_type IN ('EVENT_ACHIEVEMENT', 'EVENT_COMPLETION')
                GROUP BY reference_id
                """, result -> {
            var totals = new HashMap<String, Long>();
            while (result.next()) totals.put(result.getString("reference_id"), result.getLong("total"));
            return totals;
        }, userId);
    }

    private Map<UUID, Instant> eventCompletions(Long userId) {
        return jdbc.query("""
                SELECT event_id, completed_at
                FROM point_event_completions
                WHERE user_id = ?
                """, result -> {
            var completions = new HashMap<UUID, Instant>();
            while (result.next())
                completions.put(result.getObject("event_id", UUID.class), result.getTimestamp("completed_at").toInstant());
            return completions;
        }, userId);
    }

    @Transactional
    public AdminPointEventDTO createEvent(AdminPointEventPatchDTO patch) {
        var event = validateEvent(UUID.randomUUID(), patch);
        jdbc.update("""
                INSERT INTO point_events(id, name, description, starts_at, ends_at, game_types,
                                         completion_reward_points, enabled, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, event.id(), event.name(), event.description(), Timestamp.from(event.startsAt()),
                timestamp(event.endsAt()), String.join(",", event.gameTypes()), event.completionRewardPoints(),
                event.enabled(), Timestamp.from(Instant.now()), Timestamp.from(Instant.now()));
        replaceEventAchievements(event.id(), patch.achievements());
        return adminEvent(findEvent(event.id()));
    }

    @Transactional
    public AdminPointEventDTO updateEvent(UUID id, AdminPointEventPatchDTO patch) {
        if (scalar("SELECT COUNT(*) FROM point_events WHERE id = ?", id) == 0)
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Point event not found");
        var event = validateEvent(id, patch);
        jdbc.update("""
                UPDATE point_events
                SET name = ?, description = ?, starts_at = ?, ends_at = ?, game_types = ?,
                    completion_reward_points = ?, enabled = ?, updated_at = ?
                WHERE id = ?
                """, event.name(), event.description(), Timestamp.from(event.startsAt()), timestamp(event.endsAt()),
                String.join(",", event.gameTypes()), event.completionRewardPoints(), event.enabled(),
                Timestamp.from(Instant.now()), id);
        replaceEventAchievements(id, patch.achievements());
        return adminEvent(findEvent(id));
    }

    @Transactional
    public AdminPointEventDTO setEventEnabled(UUID id, boolean enabled) {
        if (scalar("SELECT COUNT(*) FROM point_events WHERE id = ?", id) == 0)
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Point event not found");
        if (enabled && scalar("SELECT COUNT(*) FROM point_event_achievements WHERE event_id = ?", id) == 0)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "An event needs at least one sub-achievement");
        jdbc.update("UPDATE point_events SET enabled = ?, updated_at = ? WHERE id = ?",
                enabled, Timestamp.from(Instant.now()), id);
        return adminEvent(findEvent(id));
    }

    @Transactional
    public AdminPointEventDTO deleteEvent(UUID id) {
        var event = adminEvent(findEvent(id));
        jdbc.update("DELETE FROM point_events WHERE id = ?", id);
        return event;
    }

    @Transactional(readOnly = true)
    public long changeLast7Days(Long userId) {
        return changeSince(userId, Instant.now().minusSeconds(7 * 86_400L));
    }

    @Transactional(readOnly = true)
    public long changeLast24Hours(Long userId) {
        return changeSince(userId, Instant.now().minusSeconds(86_400L));
    }

    @Transactional(readOnly = true)
    public Map<Long, Long> changesLast7Days(List<Long> userIds) {
        return changesSince(userIds, Instant.now().minusSeconds(7 * 86_400L));
    }

    @Transactional(readOnly = true)
    public Map<Long, Long> changesLast24Hours(List<Long> userIds) {
        return changesSince(userIds, Instant.now().minusSeconds(86_400L));
    }

    private Map<Long, Long> changesSince(List<Long> userIds, Instant since) {
        if (userIds.isEmpty()) return Map.of();
        var placeholders = String.join(",", java.util.Collections.nCopies(userIds.size(), "?"));
        return jdbc.query("""
                        SELECT user_id, COALESCE(SUM(amount), 0) AS change
                        FROM point_transactions
                        WHERE created_at >= ? AND user_id IN (%s)
                        GROUP BY user_id
                        """.formatted(placeholders), result -> {
                    var changes = new HashMap<Long, Long>();
                    while (result.next()) changes.put(result.getLong("user_id"), result.getLong("change"));
                    return changes;
                }, queryArguments(userIds, since));
    }

    @Transactional(readOnly = true)
    public EconomySnapshot economySnapshot() {
        var since = Timestamp.from(Instant.now().minusSeconds(7 * 86_400L));
        var today = LocalDate.now(POINTS_ZONE).toString();
        var escrowed = scalar("""
                SELECT COALESCE(SUM(p.stake_points), 0)
                FROM point_wager_players p JOIN point_wagers w ON w.game_id = p.game_id
                WHERE w.status = 'OPEN'
                """);
        return new EconomySnapshot(
                Math.addExact(scalar("SELECT COALESCE(SUM(points_balance), 0) FROM users"), escrowed),
                scalar("SELECT COALESCE(SUM(amount), 0) FROM point_transactions WHERE created_at >= ?", since),
                scalar("""
                        SELECT COALESCE(SUM(amount), 0) FROM point_transactions
                        WHERE created_at >= ? AND amount > 0
                          AND transaction_type IN ('INITIAL_GRANT','DAILY_CLAIM','GAME_PLAYED','GAME_WON','ACHIEVEMENT',
                                                   'EVENT_ACHIEVEMENT','EVENT_COMPLETION','ADMIN_ADJUSTMENT')
                        """, since),
                scalar("SELECT COALESCE(SUM(rake_points), 0) FROM point_wagers WHERE settled_at >= ?", since),
                escrowed,
                scalar("SELECT COUNT(*) FROM point_transactions WHERE transaction_type = 'DAILY_CLAIM' AND reference_id = ?", today));
    }

    @Transactional(readOnly = true)
    public AdminEconomyDashboardDTO adminDashboard() {
        var now = Instant.now();
        var since24Hours = Timestamp.from(now.minusSeconds(86_400L));
        var since7Days = Timestamp.from(now.minusSeconds(7 * 86_400L));
        var since30Days = Timestamp.from(now.minusSeconds(30 * 86_400L));
        var dailySince = Timestamp.from(LocalDate.now(POINTS_ZONE).minusDays(29).atStartOfDay(POINTS_ZONE).toInstant());
        var accounts = scalar("SELECT COALESCE(SUM(points_balance), 0) FROM users");
        var escrow = scalar("""
                SELECT COALESCE(SUM(p.stake_points), 0)
                FROM point_wager_players p JOIN point_wagers w ON w.game_id = p.game_id
                WHERE w.status = 'OPEN'
                """);
        var dailyChanges = jdbc.query("""
                SELECT CAST(created_at AT TIME ZONE 'Europe/Zagreb' AS DATE) AS day,
                       COALESCE(SUM(amount), 0) AS change
                FROM point_transactions
                WHERE created_at >= ?
                GROUP BY CAST(created_at AT TIME ZONE 'Europe/Zagreb' AS DATE)
                ORDER BY day
                """, (result, row) -> Map.entry(result.getObject("day", LocalDate.class), result.getLong("change")),
                dailySince);
        var changesByDay = new HashMap<LocalDate, Long>();
        long windowChange = 0;
        for (var entry : dailyChanges) {
            changesByDay.put(entry.getKey(), entry.getValue());
            windowChange = Math.addExact(windowChange, entry.getValue());
        }
        var dailyTotals = new ArrayList<AdminEconomyDashboardDTO.DailyTotal>();
        var day = LocalDate.now(POINTS_ZONE).minusDays(29);
        var running = Math.subtractExact(accounts, windowChange);
        for (int index = 0; index < 30; index++) {
            var change = changesByDay.getOrDefault(day, 0L);
            running = Math.addExact(running, change);
            dailyTotals.add(new AdminEconomyDashboardDTO.DailyTotal(day, running, change));
            day = day.plusDays(1);
        }
        var leaders = jdbc.query("""
                SELECT id, username, points_balance
                FROM users
                WHERE enabled = TRUE AND status = 'ACTIVE'
                ORDER BY points_balance DESC, LOWER(username), id
                LIMIT 10
                """, (result, row) -> new AdminEconomyDashboardDTO.LeaderboardEntry(
                row + 1L, result.getLong("id"), result.getString("username"), result.getLong("points_balance")));
        return new AdminEconomyDashboardDTO(
                wagerFeePercent(), accounts, escrow, Math.addExact(accounts, escrow),
                scalar("SELECT COALESCE(SUM(amount), 0) FROM point_transactions WHERE created_at >= ?", since24Hours),
                scalar("SELECT COALESCE(SUM(amount), 0) FROM point_transactions WHERE created_at >= ?", since7Days),
                scalar("SELECT COALESCE(SUM(amount), 0) FROM point_transactions WHERE created_at >= ?", since30Days),
                scalar("""
                        SELECT COALESCE(SUM(amount), 0) FROM point_transactions
                        WHERE amount > 0 AND transaction_type NOT IN ('WAGER_PAYOUT', 'WAGER_REFUND')
                        """),
                Math.addExact(scalar("""
                        SELECT COALESCE(-SUM(amount), 0) FROM point_transactions
                        WHERE amount < 0 AND transaction_type <> 'WAGER_STAKE'
                        """), scalar("SELECT COALESCE(SUM(rake_points), 0) FROM point_wagers")),
                scalar("SELECT COUNT(*) FROM point_wagers"),
                scalar("SELECT COUNT(*) FROM point_wagers WHERE status = 'OPEN'"),
                scalar("SELECT COALESCE(MAX(stake_points), 0) FROM point_wagers"),
                scalar("SELECT COALESCE(SUM(stake_points), 0) FROM point_wager_players"),
                scalar("SELECT COALESCE(SUM(payout_points), 0) FROM point_wager_players"),
                scalar("SELECT COALESCE(SUM(rake_points), 0) FROM point_wagers"),
                scalar("SELECT COUNT(*) FROM point_transactions WHERE transaction_type = 'DAILY_CLAIM' AND reference_id = ?",
                        LocalDate.now(POINTS_ZONE).toString()),
                dailyTotals, leaders);
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void applyBasicUserPointsDefault() {
        jdbc.update("UPDATE point_settings SET starting_balance = ? WHERE id = 1 AND updated_by IS NULL",
                basicUserPoints);
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void refundInterruptedWagers() {
        var gameIds = jdbc.query("SELECT game_id FROM point_wagers WHERE status = 'OPEN' ORDER BY created_at",
                (result, row) -> result.getObject(1, UUID.class));
        for (var gameId : gameIds) refundWager(gameId);
    }

    private void settleWager(UUID gameId, Map<Long, UserEntity> locked, Set<Long> rawWinnerIds) {
        var wager = jdbc.query("SELECT stake_points, status, fee_percent FROM point_wagers WHERE game_id = ? FOR UPDATE",
                (result, row) -> new WagerRow(result.getLong(1), result.getString(2), result.getInt(3)), gameId);
        if (wager.isEmpty() || !"OPEN".equals(wager.getFirst().status())) return;

        var participants = jdbc.query("""
                        SELECT user_id FROM point_wager_players WHERE game_id = ? ORDER BY user_id
                        """, (result, row) -> result.getLong(1), gameId);
        var winners = new ArrayList<Long>();
        for (var userId : participants) if (rawWinnerIds.contains(userId)) winners.add(userId);
        if (winners.isEmpty() || winners.size() == participants.size()) {
            refundWager(gameId, locked, participants, wager.getFirst().stake());
            return;
        }

        var stake = wager.getFirst().stake();
        var loserPool = Math.multiplyExact(stake, participants.size() - winners.size());
        var distributable = Math.multiplyExact(loserPool, 100 - wager.getFirst().feePercent()) / 100;
        var share = distributable / winners.size();
        var remainder = distributable % winners.size();
        for (int i = 0; i < winners.size(); i++) {
            var userId = winners.get(i);
            var payout = Math.addExact(stake, share + (i < remainder ? 1 : 0));
            applyOnce(locked.get(userId), payout, Type.WAGER_PAYOUT, gameId.toString());
            jdbc.update("UPDATE point_wager_players SET payout_points = ? WHERE game_id = ? AND user_id = ?",
                    payout, gameId, userId);
        }
        jdbc.update("""
                UPDATE point_wagers SET status = 'SETTLED', rake_points = ?, settled_at = ? WHERE game_id = ?
                """, loserPool - distributable, Timestamp.from(Instant.now()), gameId);
    }

    private void refundWager(UUID gameId) {
        var row = jdbc.query("SELECT stake_points FROM point_wagers WHERE game_id = ? AND status = 'OPEN' FOR UPDATE",
                (result, number) -> result.getLong(1), gameId);
        if (row.isEmpty()) return;
        var participants = jdbc.query("SELECT user_id FROM point_wager_players WHERE game_id = ? ORDER BY user_id",
                (result, number) -> result.getLong(1), gameId);
        refundWager(gameId, lock(participants), participants, row.getFirst());
    }

    private void refundWager(UUID gameId, Map<Long, UserEntity> locked, List<Long> participants, long stake) {
        for (var userId : participants) {
            applyOnce(locked.get(userId), stake, Type.WAGER_REFUND, gameId.toString());
            jdbc.update("UPDATE point_wager_players SET payout_points = ? WHERE game_id = ? AND user_id = ?",
                    stake, gameId, userId);
        }
        jdbc.update("""
                UPDATE point_wagers SET status = 'REFUNDED', rake_points = 0, settled_at = ? WHERE game_id = ?
                """, Timestamp.from(Instant.now()), gameId);
    }

    private int wagerFeePercent() {
        var value = jdbc.queryForObject("SELECT wager_fee_percent FROM point_settings WHERE id = 1", Integer.class);
        return value == null ? DEFAULT_WAGER_FEE_PERCENT : value;
    }

    /**
     * The fee a game actually pays: the mode's own override first, then the game-wide
     * one, then the global setting.
     */
    private int wagerFeePercent(GameType gameType, String mode) {
        if (gameType == null) return wagerFeePercent();
        var overrides = wagerFeeOverrides(gameType);
        if (mode != null && overrides.containsKey(mode)) return overrides.get(mode);
        var game = overrides.get(ALL_MODES);
        return game == null ? wagerFeePercent() : game;
    }

    private Map<String, Integer> wagerFeeOverrides(GameType gameType) {
        var overrides = new HashMap<String, Integer>();
        jdbc.query("SELECT mode, fee_percent FROM point_wager_fees WHERE game_type = ?", result -> {
            overrides.put(result.getString("mode"), result.getInt("fee_percent"));
        }, gameType.name());
        return overrides;
    }

    /** Every stored override, keyed {@code GAME:MODE} with {@code *} for the game-wide row. */
    @Transactional(readOnly = true)
    public Map<String, Integer> wagerFees() {
        var fees = new HashMap<String, Integer>();
        jdbc.query("SELECT game_type, mode, fee_percent FROM point_wager_fees", result -> {
            fees.put(result.getString("game_type") + ':' + result.getString("mode"), result.getInt("fee_percent"));
        });
        return fees;
    }

    @Transactional
    public void setWagerFee(GameType gameType, String mode, int percent) {
        if (percent < 0 || percent > 100)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bet fee must be between 0% and 100%");
        var key = mode == null ? ALL_MODES : mode;
        var updated = jdbc.update("UPDATE point_wager_fees SET fee_percent = ? WHERE game_type = ? AND mode = ?",
                percent, gameType.name(), key);
        if (updated == 0) jdbc.update("INSERT INTO point_wager_fees(game_type, mode, fee_percent) VALUES (?, ?, ?)",
                gameType.name(), key, percent);
    }

    @Transactional
    public void resetWagerFee(GameType gameType, String mode) {
        jdbc.update("DELETE FROM point_wager_fees WHERE game_type = ? AND mode = ?",
                gameType.name(), mode == null ? ALL_MODES : mode);
    }

    private List<EventRow> eventRows(boolean visibleOnly) {
        var suffix = visibleOnly
                ? " WHERE enabled AND (ends_at IS NULL OR ends_at > CURRENT_TIMESTAMP)"
                : "";
        return jdbc.query("""
                SELECT id, name, description, starts_at, ends_at, game_types,
                       completion_reward_points, enabled
                FROM point_events%s
                ORDER BY starts_at, name, id
                """.formatted(suffix), (result, row) -> new EventRow(
                result.getObject("id", UUID.class), result.getString("name"), result.getString("description"),
                result.getTimestamp("starts_at").toInstant(),
                result.getTimestamp("ends_at") == null ? null : result.getTimestamp("ends_at").toInstant(),
                parseList(result.getString("game_types")), result.getLong("completion_reward_points"),
                result.getBoolean("enabled")));
    }

    private EventRow findEvent(UUID id) {
        var rows = jdbc.query("""
                SELECT id, name, description, starts_at, ends_at, game_types,
                       completion_reward_points, enabled
                FROM point_events WHERE id = ?
                """, (result, row) -> new EventRow(
                result.getObject("id", UUID.class), result.getString("name"), result.getString("description"),
                result.getTimestamp("starts_at").toInstant(),
                result.getTimestamp("ends_at") == null ? null : result.getTimestamp("ends_at").toInstant(),
                parseList(result.getString("game_types")), result.getLong("completion_reward_points"),
                result.getBoolean("enabled")), id);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Point event not found");
        return rows.getFirst();
    }

    private List<GoalRow> eventAchievements(UUID eventId) {
        return jdbc.query("""
                SELECT id, name, description, games_required, wins_required, losses_required,
                       draws_required, reward_points, game_types, game_modes, hidden
                FROM point_event_achievements
                WHERE event_id = ? ORDER BY sort_order, id
                """, (result, row) -> new GoalRow(
                result.getObject("id", UUID.class), result.getString("name"), result.getString("description"),
                result.getInt("games_required"), result.getInt("wins_required"),
                result.getInt("losses_required"), result.getInt("draws_required"),
                result.getLong("reward_points"), parseList(result.getString("game_types")),
                parseList(result.getString("game_modes")),
                result.getBoolean("hidden")), eventId);
    }

    private AdminPointEventDTO adminEvent(EventRow event) {
        var achievements = new ArrayList<AdminPointEventDTO.Achievement>();
        for (var goal : eventAchievements(event.id()))
            achievements.add(new AdminPointEventDTO.Achievement(goal.id(), goal.name(), goal.description(),
                    markdown.render(goal.description()), goal.gamesRequired(), goal.winsRequired(),
                    goal.lossesRequired(), goal.drawsRequired(),
                    goal.rewardPoints(), goal.gameTypes(), goal.gameModes(), goal.hidden()));
        return new AdminPointEventDTO(event.id(), event.name(), event.description(), markdown.render(event.description()),
                event.startsAt(), event.endsAt(), event.gameTypes(), event.completionRewardPoints(), event.enabled(),
                achievements);
    }

    private PointEventDTO pointEvent(EventRow event, Long userId, Map<String, Long> earned,
                                     Map<UUID, Instant> completions) {
        var achievements = new ArrayList<PointEventDTO.Achievement>();
        var earnedPoints = earned.getOrDefault(event.id().toString(), 0L);
        var hiddenAchievementCount = 0;
        // ponytail: one progress query per goal, so the page costs O(events × goals)
        // round trips. Fold them into a single grouped query if the list ever grows.
        for (var goal : eventAchievements(event.id())) {
            var progress = eventProgress(userId, event, goal.gameTypes(), goal.gameModes());
            var completed = complete(goal, progress);
            if (goal.hidden() && !completed) {
                hiddenAchievementCount++;
                continue;
            }
            earnedPoints += earned.getOrDefault(goal.id().toString(), 0L);
            achievements.add(new PointEventDTO.Achievement(goal.id(), goal.name(), goal.description(),
                    markdown.render(goal.description()), goal.rewardPoints(), progress.games(),
                    goal.gamesRequired(), progress.wins(),
                    goal.winsRequired(), progress.losses(), goal.lossesRequired(), progress.draws(),
                    goal.drawsRequired(), completed, goal.gameTypes(), goal.gameModes()));
        }
        var now = Instant.now();
        var completedAt = completions.get(event.id());
        var active = !event.startsAt().isAfter(now) && (event.endsAt() == null || event.endsAt().isAfter(now))
                && !(event.endsAt() == null && completedAt != null);
        return new PointEventDTO(event.id(), event.name(), event.description(), markdown.render(event.description()),
                event.startsAt(), event.endsAt(), completedAt, event.gameTypes(), event.completionRewardPoints(), active,
                earnedPoints, hiddenAchievementCount, achievements);
    }

    private EventProgress eventProgress(Long userId, EventRow event, List<String> goalGameTypes) {
        return eventProgress(userId, event, goalGameTypes, List.of(), null);
    }

    private EventProgress eventProgress(Long userId, EventRow event, List<String> goalGameTypes,
                                        List<String> goalGameModes) {
        return eventProgress(userId, event, goalGameTypes, goalGameModes, null);
    }

    private EventProgress eventProgress(Long userId, EventRow event, List<String> goalGameTypes,
                                        List<String> goalGameModes,
                                        UUID excludedGameId) {
        var sql = new StringBuilder("""
                SELECT COUNT(*) AS games,
                       COUNT(*) FILTER (WHERE outcome = 'WIN') AS wins,
                       COUNT(*) FILTER (WHERE outcome = 'LOSS') AS losses,
                       COUNT(*) FILTER (WHERE outcome = 'DRAW') AS draws
                FROM point_game_results
                WHERE user_id = ? AND ended_at >= ?
                """);
        var args = new ArrayList<Object>();
        args.add(userId);
        args.add(Timestamp.from(event.startsAt()));
        if (event.endsAt() != null) {
            sql.append(" AND ended_at < ?");
            args.add(Timestamp.from(event.endsAt()));
        }
        if (excludedGameId != null) {
            sql.append(" AND game_id <> ?");
            args.add(excludedGameId);
        }
        var gameTypes = goalGameTypes.isEmpty() ? event.gameTypes() : goalGameTypes;
        if (!gameTypes.isEmpty()) {
            sql.append(" AND game_type IN (")
                    .append(String.join(",", java.util.Collections.nCopies(gameTypes.size(), "?")))
                    .append(')');
            args.addAll(gameTypes);
        }
        if (!goalGameModes.isEmpty()) {
            sql.append(" AND game_mode IN (")
                    .append(String.join(",", java.util.Collections.nCopies(goalGameModes.size(), "?")))
                    .append(')');
            args.addAll(goalGameModes);
        }
        return jdbc.query(sql.toString(), result -> {
            result.next();
            return new EventProgress(result.getLong("games"), result.getLong("wins"),
                    result.getLong("losses"), result.getLong("draws"));
        }, args.toArray());
    }

    private void awardEventAchievements(UserEntity user, GameType completedGameType, UUID completedGameId) {
        var now = Instant.now();
        for (var event : eventRows(true)) {
            if (event.startsAt().isAfter(now) || (event.endsAt() != null && !event.endsAt().isAfter(now))) continue;
            if (completedGameType != null && !event.gameTypes().isEmpty()
                    && !event.gameTypes().contains(completedGameType.name())) continue;
            var goals = eventAchievements(event.id());
            if (goals.isEmpty()) continue;
            var allComplete = true;
            for (var goal : goals) {
                var progress = eventProgress(user.getId(), event, goal.gameTypes(), goal.gameModes());
                var complete = complete(goal, progress);
                allComplete &= complete;
                if (complete && goal.rewardPoints() > 0
                        && applyOnce(user, goal.rewardPoints(), Type.EVENT_ACHIEVEMENT, goal.id().toString()))
                    notifyEventAchievement(user, event.name(), goal.name(), goal.rewardPoints());
                if (complete && goal.rewardPoints() == 0 && completedGameId != null
                        && !complete(goal, eventProgress(user.getId(), event, goal.gameTypes(), goal.gameModes(),
                        completedGameId)))
                    notifyEventAchievement(user, event.name(), goal.name(), 0);
            }
            if (allComplete && recordEventCompletion(user.getId(), event.id())) {
                if (event.completionRewardPoints() > 0)
                    applyOnce(user, event.completionRewardPoints(), Type.EVENT_COMPLETION, event.id().toString());
                notifyEventCompletion(user, event.name(), event.completionRewardPoints());
            }
        }
    }

    private boolean recordEventCompletion(Long userId, UUID eventId) {
        return jdbc.update("""
                INSERT INTO point_event_completions(event_id, user_id, completed_at)
                VALUES (?, ?, ?)
                ON CONFLICT (event_id, user_id) DO NOTHING
                """, eventId, userId, Timestamp.from(Instant.now())) > 0;
    }

    private boolean complete(GoalRow goal, EventProgress progress) {
        return progress.games() >= goal.gamesRequired()
                && progress.wins() >= goal.winsRequired()
                && progress.losses() >= goal.lossesRequired()
                && progress.draws() >= goal.drawsRequired();
    }

    private EventRow validateEvent(UUID id, AdminPointEventPatchDTO patch) {
        if (patch == null || patch.name() == null || patch.name().isBlank())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Event name is required");
        if (patch.startsAt() == null)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Event start date is required");
        if (patch.endsAt() != null && !patch.endsAt().isAfter(patch.startsAt()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Event end must be after its start");
        var reward = patch.completionRewardPoints() == null ? 0 : patch.completionRewardPoints();
        if (reward < 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Event reward cannot be negative");
        var gameTypes = new ArrayList<String>();
        if (patch.gameTypes() != null) {
            for (var value : patch.gameTypes()) {
                if (value == null || value.isBlank()) continue;
                var gameType = value.trim().toUpperCase();
                try {
                    GameType.valueOf(gameType);
                } catch (IllegalArgumentException error) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown game type: " + value);
                }
                if (!gameTypes.contains(gameType)) gameTypes.add(gameType);
            }
        }
        validateEventAchievements(patch.achievements(), gameTypes);
        return new EventRow(id, patch.name().trim(), patch.description() == null ? "" : patch.description().trim(),
                patch.startsAt(), patch.endsAt(), gameTypes, reward, patch.enabled() == null || patch.enabled());
    }

    private void validateEventAchievements(List<AdminPointEventPatchDTO.Achievement> achievements,
                                           List<String> eventGameTypes) {
        if (achievements == null || achievements.isEmpty())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "An event needs at least one sub-achievement");
        for (var achievement : achievements) {
            if (achievement == null || achievement.name() == null || achievement.name().isBlank())
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Every event achievement needs a name");
            var games = count(achievement.gamesRequired());
            var wins = count(achievement.winsRequired());
            var losses = count(achievement.lossesRequired());
            var draws = count(achievement.drawsRequired());
            if (games == 0 && wins == 0 && losses == 0 && draws == 0)
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Every event achievement needs at least one games, wins, losses, or draws target");
            if (achievement.rewardPoints() != null && achievement.rewardPoints() < 0)
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Achievement reward cannot be negative");
            var gameTypes = goalGameTypes(achievement.gameTypes(), eventGameTypes);
            goalGameModes(achievement.gameModes(), gameTypes);
        }
    }

    private List<String> goalGameTypes(List<String> values, List<String> eventGameTypes) {
        var result = new ArrayList<String>();
        if (values == null) return result;
        for (var value : values) {
            if (value == null || value.isBlank()) continue;
            var gameType = value.trim().toUpperCase();
            try {
                GameType.valueOf(gameType);
            } catch (IllegalArgumentException error) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown achievement game type: " + value);
            }
            if (!eventGameTypes.isEmpty() && !eventGameTypes.contains(gameType))
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Achievement game type must be allowed by its event: " + value);
            if (!result.contains(gameType)) result.add(gameType);
        }
        return result;
    }

    private List<String> goalGameModes(List<String> values, List<String> goalGameTypes) {
        var result = new ArrayList<String>();
        if (values == null || values.isEmpty()) return result;
        for (var value : values)
            if (value != null && !value.isBlank()) result.add(value.trim().toUpperCase());
        if (result.isEmpty()) return result;
        if (goalGameTypes.size() != 1)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Game modes require exactly one achievement game type");
        var game = GameType.valueOf(goalGameTypes.getFirst());
        var allowed = gameAvailability.modes(game);
        var normalized = new ArrayList<String>();
        for (var mode : result) {
            if (!allowed.contains(mode))
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Unknown " + game.name() + " achievement mode: " + mode);
            if (!normalized.contains(mode)) normalized.add(mode);
        }
        return normalized;
    }

    private void replaceEventAchievements(UUID eventId, List<AdminPointEventPatchDTO.Achievement> achievements) {
        var eventGameTypes = findEvent(eventId).gameTypes();
        var existingIds = jdbc.query("""
                        SELECT id FROM point_event_achievements
                        WHERE event_id = ? ORDER BY sort_order, id
                        """, (result, row) -> result.getObject(1, UUID.class), eventId);
        jdbc.update("DELETE FROM point_event_achievements WHERE event_id = ?", eventId);
        var ids = new LinkedHashSet<UUID>();
        for (int index = 0; index < achievements.size(); index++) {
            var achievement = achievements.get(index);
            var games = count(achievement.gamesRequired());
            var wins = count(achievement.winsRequired());
            var losses = count(achievement.lossesRequired());
            var draws = count(achievement.drawsRequired());
            var reward = achievement.rewardPoints() == null ? 0 : achievement.rewardPoints();
            var gameTypes = goalGameTypes(achievement.gameTypes(), eventGameTypes);
            var gameModes = goalGameModes(achievement.gameModes(), gameTypes);
            var id = achievement.id() != null ? achievement.id()
                    : index < existingIds.size() ? existingIds.get(index) : UUID.randomUUID();
            if (!ids.add(id)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Duplicate achievement ID");
            jdbc.update("""
                    INSERT INTO point_event_achievements(id, event_id, name, description, games_required,
                                                         wins_required, losses_required, draws_required,
                                                         reward_points, game_types, game_modes, hidden, sort_order)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, id, eventId, achievement.name().trim(),
                    achievement.description() == null ? "" : achievement.description().trim(),
                    games, wins, losses, draws, reward, String.join(",", gameTypes), String.join(",", gameModes),
                    achievement.hidden() != null && achievement.hidden(), index);
        }
    }

    private int count(Integer value) {
        if (value == null) return 0;
        if (value < 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Achievement targets cannot be negative");
        return value;
    }

    private List<String> parseList(String value) {
        if (value == null || value.isBlank()) return List.of();
        return List.of(value.split(","));
    }

    private Timestamp timestamp(Instant value) {
        return value == null ? null : Timestamp.from(value);
    }

    /**
     * Goals reset with the Points day, so today's play is what counts and every
     * grant is stamped with its date.
     */
    private DailyProgress todayProgress(Long userId) {
        var since = Timestamp.from(LocalDate.now(POINTS_ZONE).atStartOfDay(POINTS_ZONE).toInstant());
        return jdbc.query("""
                SELECT COUNT(*) AS games,
                       COUNT(*) FILTER (WHERE outcome = 'WIN') AS wins,
                       COUNT(*) FILTER (WHERE outcome = 'LOSS') AS losses,
                       COUNT(*) FILTER (WHERE outcome = 'DRAW') AS draws
                FROM point_game_results
                WHERE user_id = ? AND ended_at >= ?
                """, result -> {
            result.next();
            return new DailyProgress(result.getLong("games"), result.getLong("wins"),
                    result.getLong("losses"), result.getLong("draws"));
        }, userId, since);
    }

    /** Dated reference: one grant per goal per day, still idempotent within it. */
    private static String reference(DailyGoalRow goal) {
        return goal.code() + ':' + LocalDate.now(POINTS_ZONE);
    }

    private List<DailyGoalRow> dailyGoalRows(boolean enabledOnly) {
        return jdbc.query("""
                SELECT code, name, metric, target, reward_points, enabled
                FROM point_daily_goals%s
                ORDER BY sort_order, code
                """.formatted(enabledOnly ? " WHERE enabled" : ""), (result, row) -> new DailyGoalRow(
                result.getString("code"), result.getString("name"), result.getString("metric"),
                result.getLong("target"), result.getLong("reward_points"), result.getBoolean("enabled")));
    }

    @Transactional(readOnly = true)
    public List<AdminDailyGoalDTO> adminDailyGoals() {
        var result = new ArrayList<AdminDailyGoalDTO>();
        for (var goal : dailyGoalRows(false))
            result.add(new AdminDailyGoalDTO(goal.code(), goal.name(), goal.metric(), goal.target(),
                    goal.rewardPoints(), goal.enabled()));
        return result;
    }

    /** The list replaces what is stored: goals left out of it are dropped. */
    @Transactional
    public List<AdminDailyGoalDTO> replaceDailyGoals(List<AdminDailyGoalDTO> goals) {
        if (goals == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Daily goals are required");
        jdbc.update("DELETE FROM point_daily_goals");
        var codes = new LinkedHashSet<String>();
        for (int index = 0; index < goals.size(); index++) {
            var goal = goals.get(index);
            if (goal == null || goal.name() == null || goal.name().isBlank())
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Every daily goal needs a name");
            var metric = goal.metric() == null ? "" : goal.metric().trim().toUpperCase();
            if (!DAILY_METRICS.contains(metric))
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown daily goal metric: " + goal.metric());
            if (goal.target() == null || goal.target() <= 0)
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Daily goal target must be above 0");
            var reward = goal.rewardPoints() == null ? 0 : goal.rewardPoints();
            if (reward < 0)
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Daily goal reward cannot be negative");
            // A new goal gets its own code so its grants never collide with a deleted one's.
            var code = goal.code() == null || goal.code().isBlank()
                    ? UUID.randomUUID().toString() : goal.code().trim();
            if (!codes.add(code)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Duplicate daily goal");
            jdbc.update("""
                    INSERT INTO point_daily_goals(code, name, metric, target, reward_points, enabled, sort_order)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                    """, code, goal.name().trim(), metric, goal.target(), reward,
                    goal.enabled() == null || goal.enabled(), index);
        }
        return adminDailyGoals();
    }

    private void awardAchievements(UserEntity user) {
        var progress = todayProgress(user.getId());
        for (var goal : dailyGoalRows(true))
            if (progress.of(goal.metric()) >= goal.target()
                    && applyOnce(user, goal.rewardPoints(), Type.ACHIEVEMENT, reference(goal)))
                notifyAchievement(user, goal.name(), goal.rewardPoints());
    }

    private List<PointsAchievementDTO> achievements(UserEntity user) {
        var progress = todayProgress(user.getId());
        var result = new ArrayList<PointsAchievementDTO>();
        for (var goal : dailyGoalRows(true))
            result.add(new PointsAchievementDTO(goal.code(), goal.name(), goal.metric(), goal.rewardPoints(),
                    Math.min(progress.of(goal.metric()), goal.target()), goal.target(),
                    transactionExists(user.getId(), Type.ACHIEVEMENT, reference(goal))));
        return result;
    }

    private record DailyProgress(long games, long wins, long losses, long draws) {
        long of(String metric) {
            return switch (metric) {
                case "WINS" -> wins;
                case "LOSSES" -> losses;
                case "DRAWS" -> draws;
                default -> games;
            };
        }
    }

    private PointsAccountDTO buildSummary(UserEntity user) {
        var today = LocalDate.now(POINTS_ZONE);
        var available = user.getPointsBalance() < startingBalance() && !today.equals(user.getLastPointsClaimDate());
        var next = today.equals(user.getLastPointsClaimDate())
                ? today.plusDays(1).atStartOfDay(POINTS_ZONE).toInstant() : null;
        return new PointsAccountDTO(user.getPointsBalance(), changeLast24Hours(user.getId()), available,
                user.getLastPointsClaimDate(), next, achievements(user));
    }

    private WagerConfigDTO normalize(WagerConfigDTO wager) {
        if (wager == null || !wager.isEnabled()) return WagerConfigDTO.disabled();
        if (wager.stakePoints() < MIN_STAKE || wager.stakePoints() > MAX_STAKE)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Bet must be between " + MIN_STAKE + "P and " + MAX_STAKE + "P");
        return wager;
    }

    private Map<Long, UserEntity> lock(List<Long> ids) {
        var ordered = new LinkedHashSet<>(ids);
        var sorted = new ArrayList<>(ordered);
        sorted.sort(Comparator.naturalOrder());
        var result = new LinkedHashMap<Long, UserEntity>();
        for (var id : sorted)
            result.put(id, users.findByIdForUpdate(id).orElseThrow(() -> notFound(id)));
        return result;
    }

    private List<Long> userIds(List<UserEntity> participants) {
        var ids = new ArrayList<Long>(participants.size());
        for (var participant : participants) ids.add(participant.getId());
        return ids;
    }

    private boolean applyOnce(UserEntity user, long amount, Type type, String reference) {
        if (transactionExists(user.getId(), type, reference)) return false;
        apply(user, amount, type, reference);
        return true;
    }

    /**
     * Pays a one-time achievement reward and tells the player. {@link AchievementService}
     * owns the "already earned" check, so this only guards against a repeat within the
     * ledger itself; a 0-Point achievement moves no money and still notifies.
     */
    void grantMilestone(UserEntity user, long reward, String code, String name) {
        if (reward > 0) applyOnce(user, reward, Type.MILESTONE, code);
        notifyAchievement(user, name, reward);
    }

    private void notifyAchievement(UserEntity user, String name, long reward) {
        notifications.createRewardNotification(user.getId(), NotificationType.ACHIEVEMENT, name, reward);
    }

    private void notifyEventAchievement(UserEntity user, String eventName, String achievementName, long reward) {
        notifications.createRewardNotification(user.getId(), NotificationType.EVENT_ACHIEVEMENT,
                eventName + " · " + achievementName, reward);
    }

    private void notifyEventCompletion(UserEntity user, String eventName, long reward) {
        notifications.createRewardNotification(user.getId(), NotificationType.EVENT_COMPLETION, eventName, reward);
    }

    private void apply(UserEntity user, long amount, Type type, String reference) {
        var next = Math.addExact(user.getPointsBalance(), amount);
        if (next < 0) throw new ResponseStatusException(HttpStatus.CONFLICT, "Insufficient Points");
        user.setPointsBalance(next);
        users.save(user);
        insert(user.getId(), amount, next, type, reference);
    }

    private void insert(Long userId, long amount, long balanceAfter, Type type, String reference) {
        jdbc.update("""
                INSERT INTO point_transactions(id, user_id, amount, balance_after, transaction_type, reference_id, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), userId, amount, balanceAfter, type.name(), reference,
                Timestamp.from(Instant.now()));
    }

    private boolean transactionExists(Long userId, Type type, String reference) {
        var count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM point_transactions
                WHERE user_id = ? AND transaction_type = ? AND reference_id = ?
                """, Long.class, userId, type.name(), reference);
        return count != null && count > 0;
    }

    private long scalar(String sql, Object... args) {
        var value = jdbc.queryForObject(sql, Long.class, args);
        return value == null ? 0 : value;
    }

    private long changeSince(Long userId, Instant since) {
        var value = jdbc.queryForObject("""
                SELECT COALESCE(SUM(amount), 0)
                FROM point_transactions
                WHERE user_id = ? AND created_at >= ?
                """, Long.class, userId, Timestamp.from(since));
        return value == null ? 0 : value;
    }

    private Object[] queryArguments(List<Long> userIds, Instant since) {
        var arguments = new Object[userIds.size() + 1];
        arguments[0] = Timestamp.from(since);
        for (int i = 0; i < userIds.size(); i++) arguments[i + 1] = userIds.get(i);
        return arguments;
    }

    private ResponseStatusException notFound(Long userId) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found: " + userId);
    }

    public record EconomySnapshot(long total, long changeLast7Days, long mintedLast7Days,
                                  long rakedLast7Days, long escrowed, long dailyClaimsToday) {
    }

    public record BalanceAdjustment(long previous, long current) {
    }

    private record WagerRow(long stake, String status, int feePercent) {
    }

    private record EventRow(UUID id, String name, String description, Instant startsAt, Instant endsAt,
                            List<String> gameTypes, long completionRewardPoints, boolean enabled) {
    }

    private record GoalRow(UUID id, String name, String description, int gamesRequired, int winsRequired,
                           int lossesRequired, int drawsRequired, long rewardPoints, List<String> gameTypes,
                           List<String> gameModes, boolean hidden) {
    }

    private record EventProgress(long games, long wins, long losses, long draws) {
    }

    private record SettingsRow(int wagerFeePercent, long startingBalance, long dailyReward) {
    }

    /** Daily goal: the target counts games finished today, not for all time. */
    private record DailyGoalRow(String code, String name, String metric, long target, long rewardPoints,
                                boolean enabled) {
    }

    private enum Type {
        INITIAL_GRANT,
        DAILY_CLAIM,
        GAME_PLAYED,
        GAME_WON,
        ACHIEVEMENT,
        WAGER_STAKE,
        WAGER_PAYOUT,
        WAGER_REFUND,
        ADMIN_ADJUSTMENT,
        EVENT_ACHIEVEMENT,
        EVENT_COMPLETION,
        MILESTONE
    }

}
