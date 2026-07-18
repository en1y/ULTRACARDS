package com.ultracards.cli;

import com.ultracards.gateway.dto.admin.AdminLobbyExtendDTO;
import com.ultracards.gateway.dto.admin.AdminLobbyDTO;
import com.ultracards.gateway.dto.admin.AdminLobbyPatchDTO;
import picocli.CommandLine.Command;
import picocli.CommandLine.Help.Ansi;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.function.Supplier;

@Command(name = "lobby", aliases = "lobbies", description = "Inspect and safely change live lobbies.",
        subcommands = {LobbyCommands.ListLobbies.class, LobbyCommands.Show.class, LobbyCommands.Update.class,
                LobbyCommands.Kick.class, LobbyCommands.Close.class, LobbyCommands.Extend.class, LobbyCommands.Watch.class})
class LobbyCommands implements Runnable {
    @Spec CommandSpec spec;

    @Override public void run() {
        spec.commandLine().usage(System.out);
    }

    @Command(name = "list", aliases = "ls", description = "List active in-memory lobbies.")
    static class ListLobbies extends CliCommand {
        public Integer call() {
            return root().withClient(client -> ok(client.admin().lobbies()));
        }
    }

    @Command(name = "show", aliases = "get", description = "Show a lobby by UUID, code, name, or owner.")
    static class Show extends CliCommand {
        @Parameters(index = "0", paramLabel = "LOBBY") String target;

        public Integer call() {
            return root().withClient(client -> ok(client.admin().lobby(resolveLobbyId(target, client.admin()::lobbies))));
        }
    }

    @Command(name = "update", aliases = "edit", description = "Change an unstarted lobby's name, visibility, or mode.")
    static class Update extends CliCommand {
        @Parameters(index = "0", paramLabel = "LOBBY") String target;
        @Option(names = "--name") String name;
        @Option(names = "--visibility") String visibility;
        @Option(names = "--mode") String mode;
        @Option(names = "--reason", required = true) String reason;

        public Integer call() {
            return root().withClient(client -> {
                var id = resolveLobbyId(target, client.admin()::lobbies);
                var patch = new AdminLobbyPatchDTO(name, visibility, mode, reason);
                var current = client.admin().lobby(id);
                if (!root().confirmChange("lobby " + id, current, patch)) return 5;
                return ok(client.admin().patchLobby(id, patch));
            });
        }
    }

    @Command(name = "kick", description = "Remove a non-owner player from a lobby.")
    static class Kick extends CliCommand {
        @Parameters(index = "0", paramLabel = "LOBBY") String target;
        @Parameters(index = "1") Long userId;
        @Option(names = "--reason", required = true) String reason;

        public Integer call() {
            return root().withClient(client -> {
                var id = resolveLobbyId(target, client.admin()::lobbies);
                var current = client.admin().lobby(id);
                if (!root().confirmChange("lobby " + id, current, "remove user " + userId)) return 5;
                return ok(client.admin().kickLobbyPlayer(id, userId, reason));
            });
        }
    }

    @Command(name = "close", aliases = {"rm", "delete"}, description = "Close a lobby and run the normal cleanup path.")
    static class Close extends CliCommand {
        @Parameters(index = "0", paramLabel = "LOBBY",
                description = "Lobby UUID, code, name, or owner ID/username.")
        String target;
        @Option(names = "--reason", required = true) String reason;

        public Integer call() {
            return root().withClient(client -> {
                var id = resolveLobbyId(target, client.admin()::lobbies);
                var current = client.admin().lobby(id);
                if (!root().confirmChange("lobby " + id, current, "state=CLOSED and remove lobby")) return 5;
                client.admin().closeLobby(id, reason);
                return ok("Lobby closed");
            });
        }

    }

    @Command(name = "extend", description = "Extend lobby expiry by 1 minute to 24 hours.")
    static class Extend extends CliCommand {
        @Parameters(index = "0", paramLabel = "LOBBY") String target;
        @Option(names = "--by", required = true) String duration;
        @Option(names = "--reason", required = true) String reason;

        public Integer call() {
            var seconds = parseDuration(duration).toSeconds();
            return root().withClient(client -> ok(client.admin().extendLobby(
                    resolveLobbyId(target, client.admin()::lobbies), new AdminLobbyExtendDTO(seconds, reason))));
        }
    }

    @Command(name = "watch", aliases = "tail", description = "Stream shared lobby events, optionally filtering by lobby.")
    static class Watch extends CliCommand {
        @Option(names = {"--lobby", "--id"}, paramLabel = "LOBBY") String target;

        public Integer call() {
            return root().withClient(client -> {
                try {
                    var id = target == null ? null : resolveLobbyId(target, client.admin()::lobbies);
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

    static UUID resolveLobbyId(String target, Supplier<List<AdminLobbyDTO>> lobbies) {
        try {
            return UUID.fromString(target);
        } catch (IllegalArgumentException ignored) {
        }
        if (target == null || target.isBlank()) throw new IllegalArgumentException("Lobby cannot be blank");

        var matches = new ArrayList<UUID>();
        for (var admin : lobbies.get()) {
            var lobby = admin.lobby();
            var host = lobby.getHost();
            var matchesLobby = target.equalsIgnoreCase(lobby.getLobbyCode())
                    || target.equalsIgnoreCase(lobby.getName())
                    || host != null && (target.equals(String.valueOf(host.getId()))
                    || target.equalsIgnoreCase(host.getName()));
            if (matchesLobby && !matches.contains(lobby.getId())) matches.add(lobby.getId());
        }
        if (matches.isEmpty()) throw new UltracardsAdminCli.CliStateException("No active lobby found for: " + target);
        if (matches.size() > 1) throw new UltracardsAdminCli.CliStateException(
                "Multiple lobbies match '" + target + "'; use a lobby UUID or code");
        return matches.getFirst();
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
