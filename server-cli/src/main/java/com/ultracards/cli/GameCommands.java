package com.ultracards.cli;

import com.ultracards.gateway.dto.admin.AdminGameAvailabilityPatchDTO;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

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
        @Option(names = "--mode", paramLabel = "MODE",
                description = "Apply only to this mode; accepts canonical Durak mode keys.",
                completionCandidates = ModeCandidates.class)
        String mode;
        @Option(names = {"-r", "--reason"}, required = true) String reason;

        abstract boolean enabled();

        public Integer call() {
            return root().withClient(client -> {
                var target = mode == null ? game.name() : game.name() + " / " + mode;
                var current = client.admin().gameAvailability().stream()
                        .filter(value -> value.game().equals(game.name()) && java.util.Objects.equals(value.mode(),
                                mode))
                        .findFirst().orElse(null);
                var proposed = "enabled=" + enabled();
                if (!root().confirmChange("game " + target, current, proposed)) return 5;
                return ok(client.admin().patchGameAvailability(game.name(),
                        new AdminGameAvailabilityPatchDTO(mode, enabled(), reason)));
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
        @Option(names = "--mode", paramLabel = "MODE",
                description = "Canonical mode key, including Durak keys such as P2_D36_NO_JOKERS_EVERYONE_PASS.",
                completionCandidates = ModeCandidates.class)
        String mode;
        @Option(names = {"-r", "--reason"}, required = true) String reason;

        public Integer call() {
            return root().withClient(client -> {
                if (!root().confirm("Reset availability for " + game + (mode == null ? "" : " / " + mode) + "?")) return 5;
                return ok(client.admin().resetGameAvailability(game.name(), mode, reason));
            });
        }
    }

    enum Game { BRISKULA, TRESETA, DURAK, POKER }

    static final class ModeCandidates implements Iterable<String> {
        @Override
        public Iterator<String> iterator() {
            var values = new ArrayList<>(List.of(
                    "TWO_PLAYERS", "TWO_PLAYERS_FOUR_CARDS_IN_HAND_EACH", "THREE_PLAYERS",
                    "FOUR_PLAYERS_NO_TEAMS", "FOUR_PLAYERS_WITH_TEAMS"));
            for (var players = 2; players <= 6; players++) {
                for (var deck : List.of(24, 36, 54)) {
                    if (deck == 24 && players > 4) continue;
                    for (var jokers : deck == 54 ? List.of("NO_JOKERS", "JOKERS") : List.of("NO_JOKERS"))
                        for (var throwers : List.of("NEIGHBORS", "EVERYONE"))
                            for (var passing : List.of("NO_PASS", "PASS"))
                                values.add("P" + players + "_D" + deck + "_" + jokers + "_" + throwers + "_" + passing);
                }
            }
            return values.iterator();
        }
    }
}
