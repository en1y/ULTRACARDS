package com.ultracards.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.ultracards.gateway.dto.admin.AdminLobbyDTO;
import com.ultracards.gateway.dto.admin.AdminDashboardDTO;
import com.ultracards.gateway.dto.admin.AdminOverviewDTO;
import com.ultracards.gateway.dto.admin.AdminPageDTO;
import com.ultracards.gateway.dto.games.GameConfigDTO;
import com.ultracards.gateway.dto.games.games.briskula.BriskulaGameConfigDTO;
import com.ultracards.gateway.dto.games.games.treseta.TresetaGameConfigDTO;
import com.ultracards.gateway.dto.leaderboard.LeaderboardMetricDTO;
import com.ultracards.gateway.dto.leaderboard.LeaderboardPageDTO;
import picocli.CommandLine.Help.Ansi;

import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.RecordComponent;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

final class OutputWriter {
    enum Format { TABLE, JSON, CSV }

    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    void print(Object value, Format format, boolean explain, boolean utc, boolean color) {
        try {
            if (format == Format.JSON) {
                System.out.println(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(value));
                return;
            }
            if (format == Format.TABLE && value instanceof String message) {
                System.out.println(style("@|bold,green ✓|@ " + message, color));
                return;
            }
            var rows = rows(value, format == Format.TABLE, utc);
            if (format == Format.CSV) printCsv(rows);
            else {
                if (explain || value instanceof AdminOverviewDTO || value instanceof AdminDashboardDTO
                        || value instanceof AdminPageDTO<?>) explain(value, utc);
                printTable(rows, color);
                if (value instanceof AdminPageDTO<?> page)
                    System.out.println(style(String.format("@|faint Page %d of %d · %,d total|@",
                            page.page() + 1, Math.max(1, page.totalPages()), page.totalElements()), color));
                if (value instanceof LeaderboardPageDTO page) printLeaderboardFooter(page, color);
            }
        } catch (Exception ex) {
            throw new IllegalStateException("Cannot format output: " + ex.getMessage(), ex);
        }
    }

    private List<List<String>> rows(Object value, boolean humanReadable, boolean utc) throws Exception {
        if (value instanceof AdminDashboardDTO dashboard) return dashboardRows(dashboard);
        if (value instanceof LeaderboardPageDTO leaderboard) return leaderboardRows(leaderboard);
        Object data = value instanceof AdminPageDTO<?> page ? page.items() : value;
        var values = data instanceof Collection<?> collection ? new ArrayList<>(collection) : List.of(data);
        if (values.isEmpty()) return List.of(List.of("Result"), List.of("No results"));
        var first = values.getFirst();
        if (first == null) return List.of(List.of("Result"), List.of(""));
        if (data instanceof Collection<?> && first instanceof AdminLobbyDTO) {
            var lobbies = new ArrayList<LobbyRow>();
            for (var item : values) lobbies.add(lobbyRow((AdminLobbyDTO) item));
            return rows(lobbies, humanReadable, utc);
        }
        if (!first.getClass().isRecord()) {
            var properties = Introspector.getBeanInfo(first.getClass(), Object.class).getPropertyDescriptors();
            if (properties.length == 0) return List.of(List.of("Result"), List.of(String.valueOf(first)));
            var output = new ArrayList<List<String>>();
            var header = new ArrayList<String>();
            for (var property : properties) header.add(title(property.getName()));
            output.add(header);
            for (var item : values) output.add(beanValues(item, properties, humanReadable, utc));
            return output;
        }
        var components = first.getClass().getRecordComponents();
        var output = new ArrayList<List<String>>();
        var header = new ArrayList<String>();
        for (var component : components) header.add(title(component.getName()));
        output.add(header);
        for (var item : values) output.add(recordValues(item, components, humanReadable, utc));
        return output;
    }

    private List<List<String>> dashboardRows(AdminDashboardDTO dashboard) {
        var overview = dashboard.overview();
        var status = dashboard.status();
        var database = dashboard.database();
        var rows = new ArrayList<List<String>>();
        rows.add(List.of("Metric", "Value"));
        rows.add(List.of("Server version", status.serverVersion()));
        rows.add(List.of("API version", String.valueOf(status.apiVersion())));
        rows.add(List.of("Uptime", duration(status.uptimeSeconds())));
        rows.add(List.of("Database", status.databaseAvailable() ? "Available" : "Unavailable"));
        var flyway = status.flywayVersion() == null && database != null ? database.flywayVersion() : status.flywayVersion();
        rows.add(List.of("Flyway", flyway == null ? "" : flyway));
        rows.add(List.of("Registered users", String.valueOf(overview.users())));
        rows.add(List.of("Online now", String.valueOf(overview.onlineUsers())));
        rows.add(List.of("Active today", String.valueOf(overview.onlineUsersToday())));
        rows.add(List.of("Valid sessions", String.valueOf(overview.validSessions())));
        rows.add(List.of("Active lobbies", String.valueOf(status.activeLobbies())));
        rows.add(List.of("Active games", String.valueOf(status.activeGames())));
        rows.add(List.of("Completed games", String.valueOf(total(overview.completedGames()))));
        rows.add(List.of("Incomplete games", String.valueOf(total(overview.incompleteGames()))));
        if (database != null && database.recordsByArea() != null)
            for (var entry : database.recordsByArea().entrySet()) rows.add(List.of(entry.getKey(), String.valueOf(entry.getValue())));
        return rows;
    }

    private List<List<String>> leaderboardRows(LeaderboardPageDTO leaderboard) {
        var rows = new ArrayList<List<String>>();
        rows.add(List.of("Rank", "Player", "Games", "Wins", "Win rate"));
        if (leaderboard.items().isEmpty()) {
            rows.add(List.of("—", "No ranked players", "", "", ""));
            return rows;
        }
        for (var entry : leaderboard.items()) rows.add(List.of(
                "#" + entry.position(),
                entry.username() + (entry.currentUser() ? " (you)" : ""),
                String.format(Locale.ROOT, "%,d", entry.gamesPlayed()),
                String.format(Locale.ROOT, "%,d", entry.wins()),
                String.format(Locale.ROOT, "%.1f%%", entry.winRate())
        ));
        return rows;
    }

    private void printLeaderboardFooter(LeaderboardPageDTO page, boolean color) {
        var scope = page.mode() != null ? page.gameType() + " / " + page.mode()
                : page.gameType() == null ? "All games" : page.gameType().name();
        System.out.println(style(String.format("@|faint Page %d of %d · %,d ranked · %s · %s|@",
                page.page() + 1, Math.max(1, page.totalPages()), page.totalElements(), page.metric(), scope), color));
        if (page.metric() == LeaderboardMetricDTO.WIN_RATE)
            System.out.println(style("@|faint Minimum " + page.minimumGames() + " games|@", color));
        if (page.currentUserPosition() != null)
            System.out.println(style("@|bold Your position: #" + page.currentUserPosition() + "|@", color));
    }

    private long total(java.util.Map<String, Long> values) {
        return values == null ? 0 : values.values().stream().mapToLong(Long::longValue).sum();
    }

    private String duration(long seconds) {
        var days = seconds / 86_400;
        var hours = seconds % 86_400 / 3_600;
        var minutes = seconds % 3_600 / 60;
        var remaining = seconds % 60;
        if (days > 0) return days + "d " + hours + "h";
        if (hours > 0) return hours + "h " + minutes + "m";
        if (minutes > 0) return minutes + "m " + remaining + "s";
        return remaining + "s";
    }

    private LobbyRow lobbyRow(AdminLobbyDTO admin) {
        var lobby = admin.lobby();
        var host = lobby.getHost();
        var owner = host == null ? "" : host.getName() + " (#" + host.getId() + ")";
        var players = lobby.getPlayers() == null ? 0 : lobby.getPlayers().size();
        return new LobbyRow(lobby.getId().toString(), lobby.getLobbyCode(), owner, players + " / " + lobby.getMaxPlayers(),
                lobbyMode(lobby.getGameConfig()), lobby.getGameType().name(), admin.state());
    }

    private String lobbyMode(GameConfigDTO config) {
        if (config instanceof BriskulaGameConfigDTO briskula)
            return lobbyMode(briskula.getNumberOfPlayers(), briskula.getCardsInHandNum(), briskula.getTeamsEnabled());
        if (config instanceof TresetaGameConfigDTO treseta)
            return lobbyMode(treseta.getNumberOfPlayers(), treseta.getCardsInHandNum(), treseta.getTeamsEnabled());
        return "";
    }

    private String lobbyMode(Integer players, Integer cards, Boolean teams) {
        if (players == null) return "";
        if (players == 2 && cards != null && cards == 4) return "TWO_PLAYERS_FOUR_CARDS_IN_HAND_EACH";
        return switch (players) {
            case 2 -> "TWO_PLAYERS";
            case 3 -> "THREE_PLAYERS";
            case 4 -> Boolean.TRUE.equals(teams) ? "FOUR_PLAYERS_WITH_TEAMS" : "FOUR_PLAYERS_NO_TEAMS";
            default -> players + "_PLAYERS";
        };
    }

    private List<String> recordValues(Object item, RecordComponent[] components, boolean humanReadable,
                                      boolean utc) throws Exception {
        var output = new ArrayList<String>();
        for (var component : components) {
            var value = component.getAccessor().invoke(item);
            output.add(text(value, humanReadable, utc));
        }
        return output;
    }

    private List<String> beanValues(Object item, PropertyDescriptor[] properties, boolean humanReadable,
                                    boolean utc) throws Exception {
        var output = new ArrayList<String>();
        for (var property : properties)
            output.add(text(property.getReadMethod().invoke(item), humanReadable, utc));
        return output;
    }

    private String text(Object value, boolean humanReadable, boolean utc) throws Exception {
        String text;
        if (value == null) text = "";
        else if (humanReadable && value instanceof Instant instant) text = formatInstant(instant, utc);
        else if (value instanceof CharSequence || value instanceof Number || value instanceof Boolean || value instanceof Enum<?>)
            text = String.valueOf(value);
        else text = mapper.writeValueAsString(value);
        return text.length() > 80 ? text.substring(0, 77) + "..." : text;
    }

    private void printTable(List<List<String>> rows, boolean color) {
        var widths = new int[rows.getFirst().size()];
        for (var row : rows) for (var i = 0; i < widths.length; i++) widths[i] = Math.max(widths[i], row.get(i).length());
        printBorder(widths, '┌', '┬', '┐');
        for (var rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
            var row = rows.get(rowIndex);
            System.out.print("│");
            for (var i = 0; i < widths.length; i++) {
                var value = rowIndex == 0 ? style("@|bold,red " + row.get(i) + "|@", color) : row.get(i);
                System.out.print(" " + value + " ".repeat(widths[i] - row.get(i).length() + 1) + "│");
            }
            System.out.println();
            if (rowIndex == 0) printBorder(widths, '├', '┼', '┤');
        }
        printBorder(widths, '└', '┴', '┘');
    }

    private void printBorder(int[] widths, char left, char middle, char right) {
        System.out.print(left);
        for (var i = 0; i < widths.length; i++) {
            System.out.print("─".repeat(widths[i] + 2));
            System.out.print(i == widths.length - 1 ? right : middle);
        }
        System.out.println();
    }

    private void printCsv(List<List<String>> rows) {
        for (var row : rows) System.out.println(row.stream().map(this::csv).reduce((left, right) -> left + "," + right).orElse(""));
    }

    private String csv(String value) { return "\"" + value.replace("\"", "\"\"") + "\""; }

    private String title(String value) {
        var words = value.replaceAll("([a-z0-9])([A-Z])", "$1 $2").replace('_', ' ');
        return Character.toUpperCase(words.charAt(0)) + words.substring(1);
    }

    private String style(String value, boolean color) {
        return (color ? Ansi.ON : Ansi.OFF).string(value);
    }

    private String formatInstant(Instant value, boolean utc) {
        var zone = utc ? ZoneOffset.UTC : ZoneId.systemDefault();
        return DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(value.atZone(zone));
    }

    private void explain(Object value, boolean utc) {
        var timeDefinition = utc ? "UTC" : ZoneId.systemDefault().getId();
        if (value instanceof AdminOverviewDTO overview) {
            System.out.println("What this shows, persisted account, valid-session, and recorded-game counts plus current in-memory lobby/game counts.");
            System.out.println("Applied filters: none. Generated: " + formatInstant(overview.generatedAt(), utc) + ".");
            System.out.println("Definitions: valid sessions have active, unexpired tokens; online users were seen within the configured presence timeout. Timestamps: " + timeDefinition + ".\n");
        } else if (value instanceof AdminDashboardDTO dashboard) {
            System.out.println("What this shows, the same server, account, session, game, lobby, and database summary used by the web admin overview.");
            System.out.println("Generated: " + formatInstant(dashboard.overview().generatedAt(), utc) + ". Timestamps: " + timeDefinition + ".\n");
        } else if (value instanceof AdminPageDTO<?> page) {
            System.out.println("What this shows, a server-side paginated administrative report.");
            System.out.println("Applied filters: page=" + page.page() + ", size=" + page.size() + ".");
            System.out.println("Definitions: total is the count before pagination. Timestamps: " + timeDefinition + ".\n");
        } else if (value instanceof LeaderboardPageDTO page) {
            System.out.println("What this shows, rankings from persisted completed-game statistics.");
            System.out.println("Applied filters: metric=" + page.metric() + ", game="
                    + (page.gameType() == null ? "all" : page.gameType()) + ", mode="
                    + (page.mode() == null ? "all" : page.mode()) + ", page=" + page.page()
                    + ", size=" + page.size() + ".");
            System.out.println("Definitions: positions use deterministic tie-breaking; win rate requires at least "
                    + page.minimumGames() + " completed games.\n");
        }
    }

    private record LobbyRow(String id, String code, String owner, String players, String gameMode, String gameType,
                            String state) {}
}
