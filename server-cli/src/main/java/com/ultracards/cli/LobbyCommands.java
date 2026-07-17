package com.ultracards.cli;

import com.ultracards.gateway.dto.admin.AdminLobbyExtendDTO;
import com.ultracards.gateway.dto.admin.AdminLobbyPatchDTO;
import picocli.CommandLine.Command;
import picocli.CommandLine.Help.Ansi;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;

@Command(name = "lobby", aliases = "lobbies", description = "Inspect and safely change live lobbies.",
        subcommands = {LobbyCommands.ListLobbies.class, LobbyCommands.Show.class, LobbyCommands.Update.class,
                LobbyCommands.Kick.class, LobbyCommands.Close.class, LobbyCommands.Extend.class, LobbyCommands.Watch.class})
class LobbyCommands implements Runnable {
    @Spec CommandSpec spec;

    @Override public void run() {
        spec.commandLine().usage(System.out);
    }

    @Command(name = "list", description = "List active in-memory lobbies.")
    static class ListLobbies extends CliCommand {
        public Integer call() {
            return root().withClient(client -> ok(client.admin().lobbies()));
        }
    }

    @Command(name = "show", description = "Show one lobby and its current players/configuration.")
    static class Show extends CliCommand {
        @Parameters(index = "0") UUID id;

        public Integer call() {
            return root().withClient(client -> ok(client.admin().lobby(id)));
        }
    }

    @Command(name = "update", description = "Change an unstarted lobby's name, visibility, or mode.")
    static class Update extends CliCommand {
        @Parameters(index = "0") UUID id;
        @Option(names = "--name") String name;
        @Option(names = "--visibility") String visibility;
        @Option(names = "--mode") String mode;
        @Option(names = "--reason", required = true) String reason;

        public Integer call() {
            return root().withClient(client -> {
                var patch = new AdminLobbyPatchDTO(name, visibility, mode, reason);
                var current = client.admin().lobby(id);
                if (!root().confirmChange("lobby " + id, current, patch)) return 5;
                return ok(client.admin().patchLobby(id, patch));
            });
        }
    }

    @Command(name = "kick", description = "Remove a non-owner player from a lobby.")
    static class Kick extends CliCommand {
        @Parameters(index = "0") UUID id;
        @Parameters(index = "1") Long userId;
        @Option(names = "--reason", required = true) String reason;

        public Integer call() {
            return root().withClient(client -> {
                var current = client.admin().lobby(id);
                if (!root().confirmChange("lobby " + id, current, "remove user " + userId)) return 5;
                return ok(client.admin().kickLobbyPlayer(id, userId, reason));
            });
        }
    }

    @Command(name = "close", description = "Close a lobby and run the normal cleanup path.")
    static class Close extends CliCommand {
        @Parameters(index = "0") UUID id;
        @Option(names = "--reason", required = true) String reason;

        public Integer call() {
            return root().withClient(client -> {
                var current = client.admin().lobby(id);
                if (!root().confirmChange("lobby " + id, current, "state=CLOSED and remove lobby")) return 5;
                client.admin().closeLobby(id, reason);
                return ok("Lobby closed");
            });
        }
    }

    @Command(name = "extend", description = "Extend lobby expiry by 1 minute to 24 hours.")
    static class Extend extends CliCommand {
        @Parameters(index = "0") UUID id;
        @Option(names = "--by", required = true) String duration;
        @Option(names = "--reason", required = true) String reason;

        public Integer call() {
            var seconds = parseDuration(duration).toSeconds();
            return root().withClient(client -> ok(client.admin().extendLobby(id, new AdminLobbyExtendDTO(seconds, reason))));
        }
    }

    @Command(name = "watch", description = "Stream shared lobby events, optionally filtering by ID.")
    static class Watch extends CliCommand {
        @Option(names = "--id") UUID id;

        public Integer call() {
            return root().withClient(client -> {
                try {
                    var socket = client.lobbySocket().join();
                    socket.subscribeToLobbies(event -> {
                        if (id == null || (event.getLobbyDto() != null && id.equals(event.getLobbyDto().getId())))
                            root().emit(event);
                    });
                    System.err.println((root().colorsEnabled() ? Ansi.ON : Ansi.OFF)
                            .string("@|bold,green ● Connected|@ · watching lobby events · press Ctrl-C to stop"));
                    new CountDownLatch(1).await();
                    return 0;
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    return 0;
                }
            });
        }
    }

    private static Duration parseDuration(String value) {
        var clean = value.trim().toLowerCase();
        try {
            var amount = Long.parseLong(clean.substring(0, clean.length() - 1));
            if (clean.endsWith("s")) return Duration.ofSeconds(amount);
            if (clean.endsWith("m")) return Duration.ofMinutes(amount);
            if (clean.endsWith("h")) return Duration.ofHours(amount);
            return Duration.ofSeconds(Long.parseLong(clean));
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Duration must look like 90s, 10m, or 2h");
        }
    }
}
