package com.ultracards.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ultracards.gateway.app.GatewayAppClient;
import com.ultracards.gateway.dto.admin.AdminApiErrorDTO;
import com.ultracards.gateway.service.ClientTokenHolder;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;
import picocli.CommandLine;
import picocli.CommandLine.*;
import picocli.CommandLine.Help.Ansi;
import picocli.CommandLine.Help.Ansi.Style;
import picocli.CommandLine.Model.CommandSpec;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.Callable;
import java.util.function.Function;

@Command(name = "ultracards-admin", mixinStandardHelpOptions = true, version = UltracardsAdminCli.VERSION,
        header = {"@|bold,red ULTRACARDS ADMIN|@", "Secure remote operations for ULTRACARDS servers."},
        description = "Manage accounts, game availability, live lobbies, reports, statistics, notifications, and audit history.",
        footer = {"", "@|faint Start here:|@", "  ultracards-admin server add local https://cards.example.com",
                "  ultracards-admin login --email admin@example.com", "", "@|faint Common tasks:|@",
                "  ultracards-admin game disable BRISKULA --mode THREE_PLAYERS --reason maintenance",
                "  ultracards-admin db edit stats --user ada --game BRISKULA --mode TWO_PLAYERS --wins 12 --reason correction",
                "", "Use @|bold help <command>|@ or @|bold <command> --help|@ for details."},
        subcommands = {ServerCommands.class, Login.class, Logout.class, WhoAmI.class,
                UserCommands.class, GameCommands.class, LobbyCommands.class, DbCommands.class, SystemCommands.class,
                NotifyCommands.class, AuditCommands.class, Shell.class, Clear.class, Completion.class, HelpCommand.class})
public class UltracardsAdminCli implements Callable<Integer> {
    static final String VERSION = "0.3.0";
    @Spec CommandSpec spec;
    @Option(names = "--output", defaultValue = "TABLE", scope = ScopeType.INHERIT, paramLabel = "FORMAT",
            description = "Output format: ${COMPLETION-CANDIDATES} (default: ${DEFAULT-VALUE}).")
    OutputWriter.Format output;
    @Option(names = {"-p", "--profile"}, scope = ScopeType.INHERIT, paramLabel = "NAME",
            description = "Use a saved server profile for this command without switching the default.")
    String profile;
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

    final ConfigStore store;
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
                .commands(Style.bold, Style.fg_red)
                .options(Style.fg_red)
                .parameters(Style.fg_yellow)
                .optionParams(Style.italic)
                .errors(Style.bold, Style.fg_red)
                .build();
        var command = new CommandLine(this);
        addHelpOptions(command);
        return command
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

    private void addHelpOptions(CommandLine command) {
        for (var child : new java.util.HashSet<>(command.getSubcommands().values())) {
            if (!(child.getCommand() instanceof HelpCommand))
                child.getCommandSpec().mixinStandardHelpOptions(true).version(VERSION);
            addHelpOptions(child);
        }
    }

    @Override public Integer call() throws Exception {
        return Shell.open(this);
    }

    <T> T withClient(Function<GatewayAppClient, T> work) {
        var profile = selectedProfile();
        var url = requireServerUrl();
        validateTransport(url);
        var holder = new ClientTokenHolder(store.tokenFor(profile));
        try (var client = new GatewayAppClient(restTemplateWithImmediateTokenPersistence(), url, wsUrl(url), holder,
                com.ultracards.gateway.app.GatewayAsync.direct())) {
            var result = work.apply(client);
            store.tokenFor(profile, holder.getToken());
            return result;
        }
    }

    RestTemplate restTemplateWithImmediateTokenPersistence() {
        var template = new org.springframework.web.client.RestTemplate();
        template.getInterceptors().add((request, body, execution) -> {
            var response = execution.execute(request, body);
            var cookies = response.getHeaders().get(org.springframework.http.HttpHeaders.SET_COOKIE);
            if (cookies != null) {
                for (var cookie : cookies) {
                    if (!cookie.startsWith("refreshToken=")) continue;
                    var token = cookie.split(";", 2)[0].split("=", 2)[1];
                    store.tokenFor(selectedProfile(), token.isBlank() ? null : token);
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

    boolean colorsEnabled() {
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
        var profile = selectedProfile();
        var url = store.url(profile);
        if (profile == null) throw new CliStateException("No active server. Run: server add <name> <url>");
        if (url == null) throw new CliStateException("Unknown server profile: " + profile);
        return url;
    }

    String selectedProfile() {
        return profile == null || profile.isBlank() ? store.activeProfile() : profile;
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

    static final class CliStateException extends IllegalStateException {
        CliStateException(String message) { super(message); }
    }

}
