package com.ultracards.server.service.leaderboard;

import com.ultracards.games.briskula.BriskulaGameConfig;
import com.ultracards.games.treseta.TresetaGameConfig;
import com.ultracards.gateway.dto.games.GameTypeDTO;
import com.ultracards.gateway.dto.leaderboard.LeaderboardEntryDTO;
import com.ultracards.gateway.dto.leaderboard.LeaderboardMetricDTO;
import com.ultracards.gateway.dto.leaderboard.LeaderboardPageDTO;
import com.ultracards.server.entity.UserEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class LeaderboardService {
    public static final int WIN_RATE_MINIMUM_GAMES = 10;
    public static final int MAX_PAGE_SIZE = 100;

    private final NamedParameterJdbcTemplate jdbc;

    @Transactional(readOnly = true)
    public LeaderboardPageDTO get(String metricValue, String gameTypeValue, String modeValue,
                                  int page, int size, UserEntity currentUser) {
        if (page < 0) throw badRequest("page must be at least 0");
        if (size < 1 || size > MAX_PAGE_SIZE)
            throw badRequest("size must be between 1 and " + MAX_PAGE_SIZE);

        var metric = parseMetric(metricValue);
        var gameType = parseGameType(gameTypeValue);
        var mode = parseMode(gameType, modeValue);
        var availableModes = availableModes(gameType);
        var minimumGames = metric == LeaderboardMetricDTO.WIN_RATE ? WIN_RATE_MINIMUM_GAMES : 1;
        var currentUserId = currentUser == null ? null : currentUser.getId();
        var params = new MapSqlParameterSource()
                .addValue("minimumGames", minimumGames)
                .addValue("currentUserId", currentUserId)
                .addValue("offset", (long) page * size)
                .addValue("size", size);

        var ranked = rankedSql(metric, gameType, mode, params);
        var metadata = jdbc.queryForMap("""
                %s
                SELECT COUNT(*) AS total_elements,
                       MAX(CASE WHEN user_id = :currentUserId THEN position END) AS current_position
                FROM ranked
                """.formatted(ranked), params);
        var totalElements = ((Number) metadata.get("total_elements")).longValue();
        var currentPositionValue = metadata.get("current_position");
        var currentPosition = currentPositionValue == null ? null : ((Number) currentPositionValue).longValue();

        var items = jdbc.query("""
                %s
                SELECT position, user_id, username, games_played, wins, win_rate
                FROM ranked
                WHERE position > :offset AND position <= :offset + :size
                ORDER BY position
                """.formatted(ranked), params, (result, row) -> new LeaderboardEntryDTO(
                result.getLong("position"),
                result.getLong("user_id"),
                result.getString("username"),
                result.getLong("games_played"),
                result.getLong("wins"),
                result.getDouble("win_rate"),
                currentUserId != null && currentUserId.equals(result.getLong("user_id"))
        ));

        var totalPages = totalElements == 0 ? 0 : (int) ((totalElements + size - 1) / size);
        return new LeaderboardPageDTO(items, page, size, totalElements, totalPages, currentPosition,
                minimumGames, metric, gameType, mode, availableModes);
    }

    private String rankedSql(LeaderboardMetricDTO metric, GameTypeDTO gameType, String mode,
                             MapSqlParameterSource params) {
        var source = totalsSql(gameType, mode, params);
        return """
                WITH totals AS (
                    %s
                ), eligible AS (
                    SELECT user_id, username, games_played, wins,
                           CASE WHEN games_played = 0 THEN 0
                                ELSE wins * 100.0 / games_played END AS win_rate
                    FROM totals
                    WHERE games_played >= :minimumGames
                ), ranked AS (
                    SELECT ROW_NUMBER() OVER (ORDER BY %s) AS position,
                           user_id, username, games_played, wins, win_rate
                    FROM eligible
                )
                """.formatted(source, orderBy(metric));
    }

    private String totalsSql(GameTypeDTO gameType, String mode, MapSqlParameterSource params) {
        if (mode != null) {
            params.addValue("mode", mode);
            if (gameType == GameTypeDTO.Briskula) return modeTotals(
                    "user_briskula_stats", "user_briskula_stats_entries",
                    "user_briskula_stats_id", "briskula_game_config");
            return modeTotals("user_treseta_stats", "user_treseta_stats_entries",
                    "user_treseta_stats_id", "treseta_game_config");
        }

        var gameFilter = "";
        if (gameType != null) {
            var storedGameTypes = gameType == GameTypeDTO.Treseta
                    ? List.of("TRESETA", "TREŠETA")
                    : List.of(gameType.name().toUpperCase(Locale.ROOT));
            params.addValue("gameTypes", storedGameTypes);
            gameFilter = "AND e.game_type IN (:gameTypes)";
        }
        return """
                SELECT u.id AS user_id, u.username,
                       SUM(e.played)::BIGINT AS games_played,
                       SUM(e.wins)::BIGINT AS wins
                FROM user_game_stats s
                JOIN users u ON u.id = s.user_id
                JOIN user_game_stats_entries e ON e.user_game_stats_id = s.id
                WHERE u.enabled = TRUE AND u.status = 'ACTIVE'
                %s
                GROUP BY u.id, u.username
                """.formatted(gameFilter);
    }

    private String modeTotals(String statsTable, String entriesTable, String foreignKey, String modeColumn) {
        return """
                SELECT u.id AS user_id, u.username,
                       e.played::BIGINT AS games_played,
                       e.wins::BIGINT AS wins
                FROM %s s
                JOIN users u ON u.id = s.user_id
                JOIN %s e ON e.%s = s.id
                WHERE u.enabled = TRUE AND u.status = 'ACTIVE' AND e.%s = :mode
                """.formatted(statsTable, entriesTable, foreignKey, modeColumn);
    }

    private String orderBy(LeaderboardMetricDTO metric) {
        return switch (metric) {
            case GAMES_PLAYED -> "games_played DESC, wins DESC, LOWER(username), user_id";
            case WINS -> "wins DESC, win_rate DESC, games_played DESC, LOWER(username), user_id";
            case WIN_RATE -> "win_rate DESC, games_played DESC, wins DESC, LOWER(username), user_id";
        };
    }

    private LeaderboardMetricDTO parseMetric(String value) {
        try {
            return LeaderboardMetricDTO.valueOf(normalize(value == null || value.isBlank() ? "GAMES_PLAYED" : value));
        } catch (IllegalArgumentException ex) {
            throw badRequest("Unknown leaderboard metric: " + value);
        }
    }

    private GameTypeDTO parseGameType(String value) {
        if (value == null || value.isBlank()) return null;
        for (var gameType : GameTypeDTO.values())
            if (gameType.name().equalsIgnoreCase(value)) return gameType;
        throw badRequest("Unknown game type: " + value);
    }

    private String parseMode(GameTypeDTO gameType, String value) {
        if (value == null || value.isBlank()) return null;
        if (gameType == null) throw badRequest("gameType is required when mode is provided");
        var mode = normalize(value);
        try {
            if (gameType == GameTypeDTO.Briskula) return BriskulaGameConfig.valueOf(mode).name();
            if (gameType == GameTypeDTO.Treseta) return TresetaGameConfig.valueOf(mode).name();
        } catch (IllegalArgumentException ex) {
            throw badRequest("Unknown " + gameType.name() + " mode: " + value);
        }
        throw badRequest("Modes are not supported for " + gameType.name());
    }

    private List<String> availableModes(GameTypeDTO gameType) {
        var modes = new java.util.ArrayList<String>();
        if (gameType == GameTypeDTO.Briskula)
            for (var mode : BriskulaGameConfig.values()) modes.add(mode.name());
        if (gameType == GameTypeDTO.Treseta)
            for (var mode : TresetaGameConfig.values()) modes.add(mode.name());
        return List.copyOf(modes);
    }

    private String normalize(String value) {
        return value.trim().replace('-', '_').replace(' ', '_').toUpperCase(Locale.ROOT);
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
