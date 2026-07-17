package com.ultracards.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ultracards.gateway.app.GatewayAppClient;
import com.ultracards.gateway.dto.admin.*;
import com.ultracards.gateway.dto.auth.VerificationCodeDTO;
import com.ultracards.gateway.service.ClientTokenHolder;
import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.UserInterruptException;
import org.jline.terminal.TerminalBuilder;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;
import picocli.AutoComplete;
import picocli.CommandLine;
import picocli.CommandLine.*;
import picocli.CommandLine.Help.Ansi;
import picocli.CommandLine.Help.Ansi.Style;
import picocli.CommandLine.Model.CommandSpec;
import picocli.shell.jline3.PicocliJLineCompleter;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.function.Function;

@Command(name = "ultracards-admin", mixinStandardHelpOptions = true, version = "0.3.0",
        header = {"@|bold,cyan ULTRACARDS ADMIN|@", "Secure remote operations for ULTRACARDS servers."},
        description = "Manage accounts, live lobbies, reports, statistics, notifications, and audit history.",
        footer = {"", "@|faint Start here:|@", "  ultracards-admin server add local https://cards.example.com",
                "  ultracards-admin login --email admin@example.com", "", "Use @|bold help <command>|@ or @|bold <command> --help|@ for details."},
        subcommands = {UltracardsAdminCli.ServerCommands.class, UltracardsAdminCli.Login.class,
                UltracardsAdminCli.Logout.class, UltracardsAdminCli.WhoAmI.class,
                UltracardsAdminCli.UserCommands.class, UltracardsAdminCli.LobbyCommands.class,
                UltracardsAdminCli.DbCommands.class, UltracardsAdminCli.SystemCommands.class,
                UltracardsAdminCli.NotifyCommands.class, UltracardsAdminCli.AuditCommands.class,
                UltracardsAdminCli.Shell.class, UltracardsAdminCli.Completion.class, HelpCommand.class})
public class UltracardsAdminCli implements Runnable {
    @Spec CommandSpec spec;
    @Option(names = "--output", defaultValue = "TABLE", scope = ScopeType.INHERIT, paramLabel = "FORMAT",
            description = "Output format: ${COMPLETION-CANDIDATES} (default: ${DEFAULT-VALUE}).")
    OutputWriter.Format output;
    @Option(names = "--explain", scope = ScopeType.INHERIT, description = "Explain report filters, counts, and timestamps.")
    boolean explain;
    @Option(names = "--allow-insecure", scope = ScopeType.INHERIT, description = "Allow plain HTTP to a non-local server.")
    boolean allowInsecure;
    @Option(names = {"-y", "--yes"}, scope = ScopeType.INHERIT, description = "Skip confirmation prompts.")
    boolean yes;
    @Option(names = {"-q", "--quiet"}, scope = ScopeType.INHERIT, description = "Suppress successful command output.")
    boolean quiet;
    @Option(names = "--debug", scope = ScopeType.INHERIT, description = "Print full stack traces for failures.")
    boolean debug;
    @Option(names = "--no-color", scope = ScopeType.INHERIT, description = "Disable ANSI colors and styling.")
    boolean noColor;
    @Option(names = "--utc", scope = ScopeType.INHERIT, description = "Display timestamps in UTC.")
    boolean utc;

    private final ConfigStore store;
    private final OutputWriter writer = new OutputWriter();
    private static final ObjectMapper ERROR_MAPPER = new ObjectMapper().findAndRegisterModules();

    public UltracardsAdminCli() { this(new ConfigStore()); }
    UltracardsAdminCli(ConfigStore store) { this.store = store; }

    public static void main(String[] args) {
        if (Arrays.asList(args).contains("--no-color")) System.setProperty("picocli.ansi", "false");
        var root = new UltracardsAdminCli();
        var command = root.commandLine();
        System.exit(command.execute(args));
    }

    CommandLine commandLine() {
        var colors = new CommandLine.Help.ColorScheme.Builder(Ansi.AUTO)
                .commands(Style.bold, Style.fg_cyan)
                .options(Style.fg_cyan)
                .parameters(Style.fg_yellow)
                .optionParams(Style.italic)
                .errors(Style.bold, Style.fg_red)
                .build();
        return new CommandLine(this)
                .setColorScheme(colors)
                .setCaseInsensitiveEnumValuesAllowed(true)
                .setSubcommandsCaseInsensitive(true)
                .setUsageHelpAutoWidth(true)
                .setUsageHelpWidth(100)
                .setParameterExceptionHandler((error, args) -> {
                    var commandLine = error.getCommandLine();
                    commandLine.getErr().println(colors.errorText("Error: " + error.getMessage()));
                    if (!UnmatchedArgumentException.printSuggestions(error, commandLine.getErr()))
                        commandLine.getErr().printf("Try '%s --help' for examples and options.%n",
                                commandLine.getCommandSpec().qualifiedName());
                    return 2;
                })
                .setExecutionExceptionHandler((error, commandLine, parseResult) -> {
                    var root = (UltracardsAdminCli) commandLine.getCommandSpec().root().userObject();
                    if (root.debug) error.printStackTrace(commandLine.getErr());
                    else {
                        var code = exitCode(error);
                        commandLine.getErr().println(colors.errorText("Error: " + message(error)));
                        var hint = hint(code);
                        if (hint != null) commandLine.getErr().println(Ansi.AUTO.string("@|faint Hint: " + hint + "|@"));
                    }
                    return exitCode(error);
                });
    }

    @Override public void run() { spec.commandLine().usage(System.out); }

    <T> T withClient(Function<GatewayAppClient, T> work) {
        var url = requireServerUrl();
        validateTransport(url);
        var holder = new ClientTokenHolder(store.token());
        try (var client = new GatewayAppClient(restTemplateWithImmediateTokenPersistence(), url, wsUrl(url), holder,
                com.ultracards.gateway.app.GatewayAsync.direct())) {
            var result = work.apply(client);
            store.token(holder.getToken());
            return result;
        } finally {
            if (holder.getToken() != null) store.token(holder.getToken());
        }
    }

    org.springframework.web.client.RestTemplate restTemplateWithImmediateTokenPersistence() {
        var template = new org.springframework.web.client.RestTemplate();
        template.getInterceptors().add((request, body, execution) -> {
            var response = execution.execute(request, body);
            var cookies = response.getHeaders().get(org.springframework.http.HttpHeaders.SET_COOKIE);
            if (cookies != null) {
                for (var cookie : cookies) {
                    if (!cookie.startsWith("refreshToken=")) continue;
                    var token = cookie.split(";", 2)[0].split("=", 2)[1];
                    store.token(token.isBlank() ? null : token);
                    break;
                }
            }
            return response;
        });
        return template;
    }

    void emit(Object value) {
        if (!quiet && value != null) writer.print(value, output, explain, utc, colorsEnabled());
    }

    private boolean colorsEnabled() {
        return !noColor && System.getenv("NO_COLOR") == null && Ansi.AUTO.enabled();
    }

    boolean confirm(String prompt) {
        if (yes) return true;
        try {
            var console = System.console();
            var answer = console != null ? console.readLine("%s [y/N] ", prompt)
                    : readLine(prompt + " [y/N] ");
            return answer != null && (answer.equalsIgnoreCase("y") || answer.equalsIgnoreCase("yes"));
        } catch (Exception ex) { return false; }
    }

    boolean confirmChange(String target, Object current, Object proposed) {
        var ansi = colorsEnabled() ? Ansi.ON : Ansi.OFF;
        System.err.println(ansi.string("@|bold,yellow Review change|@"));
        System.err.println("  Target    " + target);
        System.err.println("  Current   " + current);
        System.err.println("  Proposed  " + proposed);
        var accepted = confirm("Apply this change?");
        if (!accepted) System.err.println(ansi.string("@|faint Cancelled; nothing changed.|@"));
        return accepted;
    }

    String promptSecret(String prompt) {
        var console = System.console();
        if (console != null) {
            var value = console.readPassword("%s", prompt);
            try { return value == null ? null : new String(value); }
            finally { if (value != null) Arrays.fill(value, ' '); }
        }
        return readLine(prompt);
    }

    private String readLine(String prompt) {
        try {
            System.err.print(prompt);
            return new BufferedReader(new InputStreamReader(System.in)).readLine();
        } catch (Exception ex) { throw new IllegalStateException("Cannot read terminal input", ex); }
    }

    private String requireServerUrl() {
        var url = store.activeUrl();
        if (url == null) throw new CliStateException("No active server. Run: server add <name> <url>");
        return url;
    }

    private void validateTransport(String url) {
        if (!url.startsWith("http://")) return;
        var local = url.matches("http://(localhost|127\\.0\\.0\\.1|\\[::1])(\\:.*)?");
        if (!local && !allowInsecure) throw new IllegalArgumentException("Plain HTTP is refused for remote servers; use HTTPS or --allow-insecure");
    }

    private String wsUrl(String url) {
        return url.replaceFirst("^https://", "wss://").replaceFirst("^http://", "ws://") + "/ws";
    }

    private static int exitCode(Throwable error) {
        if (error instanceof ParameterException || error instanceof IllegalArgumentException) return 2;
        if (error instanceof CliStateException) return 5;
        if (error instanceof ResourceAccessException) return 6;
        if (error instanceof RestClientResponseException response) {
            return switch (response.getStatusCode().value()) {
                case 400, 422 -> 2;
                case 401 -> 3;
                case 403 -> 4;
                case 404, 409 -> 5;
                default -> 7;
            };
        }
        return 7;
    }

    private static String message(Throwable error) {
        if (error instanceof RestClientResponseException response) {
            var body = response.getResponseBodyAsString();
            if (body == null || body.isBlank()) return response.getStatusText();
            try {
                var apiError = ERROR_MAPPER.readValue(body, AdminApiErrorDTO.class);
                var details = new ArrayList<String>();
                if (apiError.errors() != null)
                    for (var entry : apiError.errors().entrySet()) details.add(entry.getKey() + ": " + entry.getValue());
                if (apiError.globalErrors() != null) details.addAll(apiError.globalErrors());
                return details.isEmpty() ? apiError.message() : apiError.message() + " (" + String.join("; ", details) + ")";
            } catch (Exception ignored) {
                return body;
            }
        }
        return error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
    }

    private static String hint(int code) {
        return switch (code) {
            case 3 -> "Log in again with: login --email <address>";
            case 4 -> "The active account must have the ADMIN role.";
            case 5 -> "Verify the target and active profile with: server list";
            case 6 -> "Check the active URL with: server list";
            default -> null;
        };
    }

    private static final class CliStateException extends IllegalStateException {
        private CliStateException(String message) { super(message); }
    }

    static abstract class Base implements Callable<Integer> {
        @Spec CommandSpec spec;
        UltracardsAdminCli root() { return (UltracardsAdminCli) spec.root().userObject(); }
        Integer ok(Object value) { root().emit(value); return 0; }
    }

    @Command(name = "server", description = "Manage named server profiles.", subcommands = {ServerAdd.class, ServerList.class, ServerUse.class, ServerRemove.class})
    static class ServerCommands implements Runnable { @Spec CommandSpec spec; public void run() { spec.commandLine().usage(System.out); } }
    @Command(name = "add", description = "Save a server URL and select it when it is the first profile.") static class ServerAdd extends Base {
        @Parameters(index = "0", paramLabel = "NAME") String name; @Parameters(index = "1", paramLabel = "URL") String url;
        public Integer call() { root().store.add(name, url); return ok("Saved server profile " + name); }
    }
    @Command(name = "list", description = "Show saved profiles and the active connection target.") static class ServerList extends Base {
        public Integer call() {
            var profiles = new ArrayList<ServerProfile>();
            for (var entry : root().store.profiles().entrySet())
                profiles.add(new ServerProfile(entry.getKey().equals(root().store.activeProfile()) ? "●" : "", entry.getKey(), entry.getValue()));
            return ok(profiles);
        }
    }
    @Command(name = "use", description = "Select the profile used by subsequent commands.") static class ServerUse extends Base {
        @Parameters(index = "0", paramLabel = "NAME") String name;
        public Integer call() { root().store.use(name); return ok("Using server profile " + name); }
    }
    @Command(name = "remove", description = "Remove a profile and its locally stored session.") static class ServerRemove extends Base {
        @Parameters(index = "0", paramLabel = "NAME") String name;
        public Integer call() {
            var current = root().store.profiles().get(name);
            if (current == null) throw new IllegalArgumentException("Unknown server profile: " + name);
            if (!root().confirmChange("server profile " + name, current, "remove profile and stored session")) return 5;
            root().store.remove(name);
            return ok("Removed server profile " + name);
        }
    }

    private record ServerProfile(String active, String name, String url) {}

    @Command(name = "login", description = "Log in with an emailed verification code.")
    static class Login extends Base {
        @Option(names = "--email", required = true, paramLabel = "ADDRESS", description = "Administrator email address.") String email;
        public Integer call() {
            return root().withClient(client -> {
                client.authentication().sendVerificationEmail(email);
                if (!root().quiet) System.err.println("Verification code sent to " + email + ".");
                var code = root().promptSecret("Verification code: ");
                if (code == null || !code.matches("\\d{6}")) throw new IllegalArgumentException("Verification code must be six digits");
                if (!client.authentication().verifyCode(new VerificationCodeDTO(code, email), client.tokenHolder()))
                    throw new IllegalArgumentException("Verification code was rejected");
                root().emit(client.authentication().getProfile());
                return 0;
            });
        }
    }
    @Command(name = "logout", description = "Revoke the current session and forget its local token.") static class Logout extends Base {
        public Integer call() {
            root().withClient(client -> { client.authentication().logout(client.tokenHolder()); return null; });
            root().store.token(null); return ok("Logged out");
        }
    }
    @Command(name = "whoami", description = "Show the account authenticated on the active server.") static class WhoAmI extends Base {
        public Integer call() { return root().withClient(client -> ok(client.authentication().getProfile())); }
    }

    @Command(name = "user", aliases = "users", description = "Inspect accounts, roles, status, and sessions.", subcommands = {UserList.class, UserShow.class, UserRole.class, UserEnable.class, UserDisable.class, UserSessions.class})
    static class UserCommands implements Runnable { @Spec CommandSpec spec; public void run() { spec.commandLine().usage(System.out); } }
    @Command(name = "list", description = "List users with server-side pagination.") static class UserList extends Base {
        @Option(names = "--page", defaultValue = "0") int page; @Option(names = "--size", defaultValue = "25") int size;
        public Integer call() { return root().withClient(client -> ok(client.admin().users(page, size))); }
    }
    @Command(name = "show", description = "Show one user by numeric ID.") static class UserShow extends Base {
        @Parameters(index = "0") Long id;
        public Integer call() { return root().withClient(client -> ok(client.admin().user(id))); }
    }
    @Command(name = "role", description = "Grant or revoke administrator and moderator roles.", subcommands = {RoleGrant.class, RoleRevoke.class})
    static class UserRole implements Runnable { @Spec CommandSpec spec; public void run() { spec.commandLine().usage(System.out); } }
    @Command(name = "grant", description = "Grant ADMIN or MODERATOR to a user.") static class RoleGrant extends Base {
        @Parameters(index = "0") Long id; @Parameters(index = "1") String role; @Option(names = "--reason", required = true) String reason;
        public Integer call() { return root().withClient(client -> ok(client.admin().grantRole(id, role, reason))); }
    }
    @Command(name = "revoke", description = "Revoke ADMIN or MODERATOR from a user.") static class RoleRevoke extends Base {
        @Parameters(index = "0") Long id; @Parameters(index = "1") String role; @Option(names = "--reason", required = true) String reason;
        public Integer call() {
            return root().withClient(client -> {
                var current = client.admin().user(id);
                if (!root().confirmChange("user " + id, current.roles(), "remove role " + role)) return 5;
                return ok(client.admin().revokeRole(id, role, reason));
            });
        }
    }
    abstract static class UserState extends Base {
        @Parameters(index = "0") Long id; @Option(names = "--reason", required = true) String reason;
        abstract boolean enabled();
        public Integer call() {
            var patch = new AdminUserPatchDTO(null, null, enabled(), enabled() ? "ACTIVE" : "DISABLED", reason, false);
            return root().withClient(client -> {
                var current = client.admin().user(id);
                if (!enabled() && !root().confirmChange("user " + id,
                        "enabled=" + current.enabled() + ", status=" + current.status(),
                        "enabled=false, status=DISABLED, revoke sessions")) return 5;
                return ok(client.admin().patchUser(id, patch));
            });
        }
    }
    @Command(name = "enable", description = "Enable an account and set it ACTIVE.") static class UserEnable extends UserState { boolean enabled() { return true; } }
    @Command(name = "disable", description = "Disable an account and revoke its sessions.") static class UserDisable extends UserState { boolean enabled() { return false; } }
    @Command(name = "sessions", description = "Manage a user's login sessions.", subcommands = UserSessionsRevoke.class)
    static class UserSessions implements Runnable { @Spec CommandSpec spec; public void run() { spec.commandLine().usage(System.out); } }
    @Command(name = "revoke", description = "Revoke every active session for a user.") static class UserSessionsRevoke extends Base {
        @Parameters(index = "0") Long id; @Option(names = "--reason", required = true) String reason;
        public Integer call() {
            return root().withClient(client -> {
                var current = client.admin().user(id);
                if (!root().confirmChange("user " + id, current, "revoke all sessions")) return 5;
                client.admin().revokeSessions(id, reason);
                return ok("Sessions revoked");
            });
        }
    }

    @Command(name = "lobby", aliases = "lobbies", description = "Inspect and safely change live lobbies.", subcommands = {LobbyList.class, LobbyShow.class, LobbyUpdate.class, LobbyKick.class, LobbyClose.class, LobbyExtend.class, LobbyWatch.class})
    static class LobbyCommands implements Runnable { @Spec CommandSpec spec; public void run() { spec.commandLine().usage(System.out); } }
    @Command(name = "list", description = "List active in-memory lobbies.") static class LobbyList extends Base { public Integer call() { return root().withClient(client -> ok(client.admin().lobbies())); } }
    @Command(name = "show", description = "Show one lobby and its current players/configuration.") static class LobbyShow extends Base {
        @Parameters(index = "0") UUID id; public Integer call() { return root().withClient(client -> ok(client.admin().lobby(id))); }
    }
    @Command(name = "update", description = "Change an unstarted lobby's name, visibility, or mode.") static class LobbyUpdate extends Base {
        @Parameters(index = "0") UUID id; @Option(names = "--name") String name;
        @Option(names = "--visibility") String visibility; @Option(names = "--mode") String mode;
        @Option(names = "--reason", required = true) String reason;
        public Integer call() { return root().withClient(client -> {
            var patch = new AdminLobbyPatchDTO(name, visibility, mode, reason);
            var current = client.admin().lobby(id);
            if (!root().confirmChange("lobby " + id, current, patch)) return 5;
            return ok(client.admin().patchLobby(id, patch));
        }); }
    }
    @Command(name = "kick", description = "Remove a non-owner player from a lobby.") static class LobbyKick extends Base {
        @Parameters(index = "0") UUID id; @Parameters(index = "1") Long userId; @Option(names = "--reason", required = true) String reason;
        public Integer call() {
            return root().withClient(client -> {
                var current = client.admin().lobby(id);
                if (!root().confirmChange("lobby " + id, current, "remove user " + userId)) return 5;
                return ok(client.admin().kickLobbyPlayer(id, userId, reason));
            });
        }
    }
    @Command(name = "close", description = "Close a lobby and run the normal cleanup path.") static class LobbyClose extends Base {
        @Parameters(index = "0") UUID id; @Option(names = "--reason", required = true) String reason;
        public Integer call() {
            return root().withClient(client -> {
                var current = client.admin().lobby(id);
                if (!root().confirmChange("lobby " + id, current, "state=CLOSED and remove lobby")) return 5;
                client.admin().closeLobby(id, reason);
                return ok("Lobby closed");
            });
        }
    }
    @Command(name = "extend", description = "Extend lobby expiry by 1 minute to 24 hours.") static class LobbyExtend extends Base {
        @Parameters(index = "0") UUID id; @Option(names = "--by", required = true) String duration; @Option(names = "--reason", required = true) String reason;
        public Integer call() { var seconds = parseDuration(duration).toSeconds(); return root().withClient(client -> ok(client.admin().extendLobby(id, new AdminLobbyExtendDTO(seconds, reason)))); }
    }
    @Command(name = "watch", description = "Stream shared lobby events, optionally filtering by ID.") static class LobbyWatch extends Base {
        @Option(names = "--id") UUID id;
        public Integer call() {
            return root().withClient(client -> {
                try {
                    var socket = client.lobbySocket().join();
                    socket.subscribeToLobbies(event -> { if (id == null || (event.getLobbyDto() != null && id.equals(event.getLobbyDto().getId()))) root().emit(event); });
                    System.err.println((root().colorsEnabled() ? Ansi.ON : Ansi.OFF)
                            .string("@|bold,green ● Connected|@ · watching lobby events · press Ctrl-C to stop"));
                    new CountDownLatch(1).await();
                    return 0;
                } catch (InterruptedException ex) { Thread.currentThread().interrupt(); return 0; }
            });
        }
    }

    @Command(name = "db", description = "Run safe database edits, rebuilds, and reports.", subcommands = {DbEdit.class, DbStats.class, DbOverview.class, DbUsers.class, DbGames.class, DbSessions.class})
    static class DbCommands implements Runnable { @Spec CommandSpec spec; public void run() { spec.commandLine().usage(System.out); } }
    @Command(name = "edit", description = "Apply typed, allowlisted field edits.", subcommands = {DbEditUser.class, DbEditGame.class, DbEditStats.class})
    static class DbEdit implements Runnable { @Spec CommandSpec spec; public void run() { spec.commandLine().usage(System.out); } }
    @Command(name = "user", description = "Edit allowlisted user fields with optional dry-run.") static class DbEditUser extends Base {
        @Parameters(index = "0") Long id; @Option(names = "--username") String username; @Option(names = "--email") String email;
        @Option(names = "--enabled") Boolean enabled; @Option(names = "--status") String status;
        @Option(names = "--reason", required = true) String reason; @Option(names = "--dry-run") boolean dryRun;
        public Integer call() { return root().withClient(client -> {
            var patch = new AdminUserPatchDTO(username, email, enabled, status, reason, dryRun);
            if (!dryRun && !root().confirmChange("user " + id, client.admin().user(id), patch)) return 5;
            return ok(client.admin().patchUser(id, patch));
        }); }
    }
    @Command(name = "game-record", description = "Rename a recorded game without changing gameplay data.") static class DbEditGame extends Base {
        @Parameters(index = "0") UUID id; @Option(names = "--name", required = true) String name;
        @Option(names = "--reason", required = true) String reason; @Option(names = "--dry-run") boolean dryRun;
        public Integer call() { return root().withClient(client -> {
            var patch = new AdminRecordedGamePatchDTO(name, reason, dryRun);
            if (!dryRun && !root().confirmChange("recorded game " + id, client.admin().game(id), patch)) return 5;
            return ok(client.admin().patchGame(id, patch));
        }); }
    }
    @Command(name = "stats", description = "Override one per-mode statistics row with an audit reason.") static class DbEditStats extends Base {
        @Option(names = "--user", required = true) Long userId; @Option(names = "--game", required = true) String game;
        @Option(names = "--mode", required = true) String mode; @Option(names = "--played", required = true) int played;
        @Option(names = "--wins", required = true) int wins; @Option(names = "--last-played-at") Instant lastPlayedAt;
        @Option(names = "--reason", required = true) String reason; @Option(names = "--dry-run") boolean dryRun;
        public Integer call() { return root().withClient(client -> {
            var patch = new AdminStatsPatchDTO(played, wins, lastPlayedAt, reason, dryRun);
            if (!dryRun && !root().confirmChange("statistics for user " + userId + " / " + game + " / " + mode,
                    client.admin().stats(userId), patch)) return 5;
            return ok(client.admin().patchStats(userId, game, mode, patch));
        }); }
    }
    @Command(name = "stats", description = "Rebuild statistics from completed recordings.", subcommands = DbStatsRebuild.class)
    static class DbStats implements Runnable { @Spec CommandSpec spec; public void run() { spec.commandLine().usage(System.out); } }
    @Command(name = "rebuild", description = "Preview or replace aggregates derived from recorded history.") static class DbStatsRebuild extends Base {
        @Option(names = "--user", required = true) Long userId; @Option(names = "--game") String game;
        @Option(names = "--reason", required = true) String reason; @Option(names = "--dry-run") boolean dryRun;
        public Integer call() { return root().withClient(client -> {
            if (!dryRun && !root().confirmChange("statistics for user " + userId,
                    client.admin().stats(userId), "rebuild from completed " + (game == null ? "all" : game) + " recordings")) return 5;
            return ok(client.admin().rebuildStats(userId, game, reason, dryRun));
        }); }
    }
    @Command(name = "overview", description = "Show account, session, game, lobby, and database totals.") static class DbOverview extends Base { public Integer call() { return root().withClient(client -> ok(client.admin().overview())); } }
    @Command(name = "users", description = "Report users with allowlisted filters and sorting.") static class DbUsers extends Base {
        @Option(names = "--page", defaultValue = "0") int page; @Option(names = "--size", defaultValue = "25") int size;
        @Option(names = "--status") String status; @Option(names = "--role") String role;
        @Option(names = "--sort") String sort; @Option(names = "--direction") String direction;
        public Integer call() { return root().withClient(client -> ok(client.admin().reportUsers(page, size, status, role, sort, direction))); }
    }
    @Command(name = "games", description = "Report completed or incomplete recorded games.") static class DbGames extends Base {
        @Option(names = "--page", defaultValue = "0") int page; @Option(names = "--size", defaultValue = "25") int size;
        @Option(names = "--game") String game; @Option(names = "--completed") Boolean completed;
        @Option(names = "--sort") String sort; @Option(names = "--direction") String direction;
        public Integer call() { return root().withClient(client -> ok(client.admin().games(page, size, game, completed, sort, direction))); }
    }
    @Command(name = "sessions", description = "Report session validity without credential material.") static class DbSessions extends Base {
        @Option(names = "--page", defaultValue = "0") int page; @Option(names = "--size", defaultValue = "25") int size;
        @Option(names = "--user") Long userId; @Option(names = "--valid") Boolean valid;
        @Option(names = "--sort") String sort; @Option(names = "--direction") String direction;
        public Integer call() { return root().withClient(client -> ok(client.admin().sessions(page, size, userId, valid, sort, direction))); }
    }

    @Command(name = "system", description = "Check server and database health.", subcommands = {SystemStatus.class, SystemDoctor.class})
    static class SystemCommands implements Runnable { @Spec CommandSpec spec; public void run() { spec.commandLine().usage(System.out); } }
    @Command(name = "status", description = "Show version, availability, and live object counts.") static class SystemStatus extends Base { public Integer call() { return root().withClient(client -> ok(client.admin().status())); } }
    @Command(name = "doctor", description = "Fail when the server or database health check is unhealthy.") static class SystemDoctor extends Base {
        public Integer call() { return root().withClient(client -> { var status = client.admin().status(); if (!status.databaseAvailable()) throw new IllegalStateException("Database health check failed"); return ok(status); }); }
    }
    @Command(name = "notify", description = "Send audited administrative notifications.", subcommands = {NotifyUser.class, NotifyAll.class})
    static class NotifyCommands implements Runnable { @Spec CommandSpec spec; public void run() { spec.commandLine().usage(System.out); } }
    @Command(name = "user", description = "Send a message to one user.") static class NotifyUser extends Base {
        @Parameters(index = "0") Long id; @Option(names = "--message", required = true) String message; @Option(names = "--reason", required = true) String reason;
        public Integer call() { return root().withClient(client -> ok(client.admin().notifyUser(id, new AdminNotificationRequestDTO(message, reason)))); }
    }
    @Command(name = "all", description = "Broadcast a message to every user after confirmation.") static class NotifyAll extends Base {
        @Option(names = "--message", required = true) String message; @Option(names = "--reason", required = true) String reason;
        public Integer call() { if (!root().confirm("Send this notification to every user?")) return 5; return root().withClient(client -> ok(client.admin().notifyAll(new AdminNotificationRequestDTO(message, reason)))); }
    }
    @Command(name = "audit", description = "Inspect immutable administrative audit history.", subcommands = {AuditList.class, AuditShow.class})
    static class AuditCommands implements Runnable { @Spec CommandSpec spec; public void run() { spec.commandLine().usage(System.out); } }
    @Command(name = "list", description = "List audit events newest first.") static class AuditList extends Base {
        @Option(names = "--page", defaultValue = "0") int page; @Option(names = "--size", defaultValue = "25") int size;
        public Integer call() { return root().withClient(client -> ok(client.admin().audit(page, size))); }
    }
    @Command(name = "show", description = "Show one audit event by UUID.") static class AuditShow extends Base {
        @Parameters(index = "0") UUID id;
        public Integer call() { return root().withClient(client -> ok(client.admin().audit(id))); }
    }

    @Command(name = "shell", description = "Open an interactive shell with history and completion.") static class Shell extends Base {
        public Integer call() throws Exception {
            try (var terminal = TerminalBuilder.builder().system(true).build()) {
                var commandLine = root().commandLine();
                var completer = new PicocliJLineCompleter(commandLine.getCommandSpec());
                var reader = LineReaderBuilder.builder().terminal(terminal).completer(completer)
                        .variable(org.jline.reader.LineReader.HISTORY_FILE, root().store.historyFile()).build();
                terminal.writer().println((root().colorsEnabled() ? Ansi.ON : Ansi.OFF).string(
                        "@|bold,cyan ULTRACARDS ADMIN SHELL|@  @|faint Type help, exit, or press Tab to explore.|@"));
                terminal.writer().flush();
                while (true) {
                    try {
                        var profile = root().store.activeProfile() == null ? "no-server" : root().store.activeProfile();
                        var prompt = (root().colorsEnabled() ? Ansi.ON : Ansi.OFF)
                                .string("@|bold,cyan ultracards|@@|faint [" + profile + "]|@> ");
                        var line = reader.readLine(prompt).trim();
                        if (line.equals("exit") || line.equals("quit")) return 0;
                        if (line.isBlank()) continue;
                        var args = new org.jline.reader.impl.DefaultParser().parse(line, 0).words().toArray(String[]::new);
                        root().commandLine().execute(args);
                    } catch (UserInterruptException ignored) {
                    } catch (EndOfFileException ex) { return 0; }
                }
            }
        }
    }
    @Command(name = "completion", description = "Generate Bash or Zsh completion setup.") static class Completion extends Base {
        @Parameters(index = "0", defaultValue = "bash") String shell;
        public Integer call() {
            var script = AutoComplete.bash("ultracards-admin", root().commandLine());
            if (shell.equalsIgnoreCase("zsh")) System.out.println("autoload -U +X bashcompinit && bashcompinit");
            else if (!shell.equalsIgnoreCase("bash")) throw new IllegalArgumentException("Completion supports bash or zsh");
            System.out.print(script);
            return 0;
        }
    }

    private static Duration parseDuration(String value) {
        var clean = value.trim().toLowerCase();
        try {
            if (clean.endsWith("s")) return Duration.ofSeconds(Long.parseLong(clean.substring(0, clean.length() - 1)));
            if (clean.endsWith("m")) return Duration.ofMinutes(Long.parseLong(clean.substring(0, clean.length() - 1)));
            if (clean.endsWith("h")) return Duration.ofHours(Long.parseLong(clean.substring(0, clean.length() - 1)));
            return Duration.ofSeconds(Long.parseLong(clean));
        } catch (NumberFormatException ex) { throw new IllegalArgumentException("Duration must look like 90s, 10m, or 2h"); }
    }
}
