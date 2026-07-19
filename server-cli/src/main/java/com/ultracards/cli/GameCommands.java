package com.ultracards.cli;

import com.ultracards.gateway.dto.admin.AdminGameAvailabilityPatchDTO;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

@Command(name = "game", aliases = "games", description = "Enable or disable game types and modes.",
        subcommands = {GameCommands.ListGames.class, GameCommands.Enable.class, GameCommands.Disable.class,
                GameCommands.Reset.class})
class GameCommands implements Runnable {
    @Spec CommandSpec spec;

    @Override public void run() { spec.commandLine().usage(System.out); }

    @Command(name = "list", aliases = "ls", description = "Show the persisted availability of every game and mode.")
    static class ListGames extends CliCommand {
        public Integer call() { return root().withClient(client -> ok(client.admin().gameAvailability())); }
    }

    abstract static class Change extends CliCommand {
        @Parameters(index = "0", paramLabel = "GAME") Game game;
        @Option(names = "--mode", paramLabel = "MODE", description = "Apply only to this mode; omit for the entire game.") Mode mode;
        @Option(names = {"-r", "--reason"}, required = true) String reason;

        abstract boolean enabled();

        public Integer call() {
            return root().withClient(client -> {
                var target = mode == null ? game.name() : game.name() + " / " + mode.name();
                var current = client.admin().gameAvailability().stream()
                        .filter(value -> value.game().equals(game.name()) && java.util.Objects.equals(value.mode(),
                                mode == null ? null : mode.name()))
                        .findFirst().orElse(null);
                var proposed = "enabled=" + enabled();
                if (!root().confirmChange("game " + target, current, proposed)) return 5;
                return ok(client.admin().patchGameAvailability(game.name(),
                        new AdminGameAvailabilityPatchDTO(mode == null ? null : mode.name(), enabled(), reason)));
            });
        }
    }

    @Command(name = "enable", description = "Enable a game or one of its modes.")
    static class Enable extends Change {
        boolean enabled() { return true; }
    }

    @Command(name = "disable", description = "Disable a game or one of its modes for new and starting lobbies.")
    static class Disable extends Change {
        boolean enabled() { return false; }
    }

    @Command(name = "reset", description = "Reset a game or mode to its default availability rule.")
    static class Reset extends CliCommand {
        @Parameters(index = "0", paramLabel = "GAME") Game game;
        @Option(names = "--mode") Mode mode;
        @Option(names = {"-r", "--reason"}, required = true) String reason;

        public Integer call() {
            return root().withClient(client -> {
                if (!root().confirm("Reset availability for " + game + (mode == null ? "" : " / " + mode) + "?")) return 5;
                return ok(client.admin().resetGameAvailability(game.name(), mode == null ? null : mode.name(), reason));
            });
        }
    }

    enum Game { BRISKULA, TRESETA, DURAK, POKER }

    enum Mode {
        TWO_PLAYERS,
        TWO_PLAYERS_FOUR_CARDS_IN_HAND_EACH,
        THREE_PLAYERS,
        FOUR_PLAYERS_NO_TEAMS,
        FOUR_PLAYERS_WITH_TEAMS
    }
}
