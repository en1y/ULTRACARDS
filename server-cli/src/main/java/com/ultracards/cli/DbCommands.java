package com.ultracards.cli;

import com.ultracards.gateway.dto.admin.AdminRecordedGamePatchDTO;
import com.ultracards.gateway.dto.admin.AdminStatsPatchDTO;
import com.ultracards.gateway.dto.admin.AdminUserPatchDTO;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

import java.time.Instant;
import java.util.UUID;

@Command(name = "db", description = "Run safe database edits, rebuilds, and reports.",
        subcommands = {DbCommands.Edit.class, DbCommands.Stats.class, DbCommands.Overview.class,
                DbCommands.Users.class, DbCommands.Games.class, DbCommands.Sessions.class})
class DbCommands implements Runnable {
    @Spec CommandSpec spec;

    @Override public void run() {
        spec.commandLine().usage(System.out);
    }

    @Command(name = "edit", description = "Apply typed, allowlisted field edits.",
            subcommands = {EditUser.class, EditGame.class, EditStats.class})
    static class Edit implements Runnable {
        @Spec CommandSpec spec;

        public void run() {
            spec.commandLine().usage(System.out);
        }
    }

    @Command(name = "user", description = "Edit allowlisted user fields with optional dry-run.")
    static class EditUser extends CliCommand {
        @Parameters(index = "0") Long id;
        @Option(names = "--username") String username;
        @Option(names = "--email") String email;
        @Option(names = "--enabled") Boolean enabled;
        @Option(names = "--status") String status;
        @Option(names = "--reason", required = true) String reason;
        @Option(names = "--dry-run") boolean dryRun;

        public Integer call() {
            return root().withClient(client -> {
                var patch = new AdminUserPatchDTO(username, email, enabled, status, reason, dryRun);
                if (!dryRun && !root().confirmChange("user " + id, client.admin().user(id), patch)) return 5;
                return ok(client.admin().patchUser(id, patch));
            });
        }
    }

    @Command(name = "game-record", description = "Rename a recorded game without changing gameplay data.")
    static class EditGame extends CliCommand {
        @Parameters(index = "0") UUID id;
        @Option(names = "--name", required = true) String name;
        @Option(names = "--reason", required = true) String reason;
        @Option(names = "--dry-run") boolean dryRun;

        public Integer call() {
            return root().withClient(client -> {
                var patch = new AdminRecordedGamePatchDTO(name, reason, dryRun);
                if (!dryRun && !root().confirmChange("recorded game " + id, client.admin().game(id), patch)) return 5;
                return ok(client.admin().patchGame(id, patch));
            });
        }
    }

    @Command(name = "stats", description = "Override one per-mode statistics row with an audit reason.")
    static class EditStats extends CliCommand {
        @Option(names = "--user", required = true) Long userId;
        @Option(names = "--game", required = true) String game;
        @Option(names = "--mode", required = true) String mode;
        @Option(names = "--played", required = true) int played;
        @Option(names = "--wins", required = true) int wins;
        @Option(names = "--last-played-at") Instant lastPlayedAt;
        @Option(names = "--reason", required = true) String reason;
        @Option(names = "--dry-run") boolean dryRun;

        public Integer call() {
            return root().withClient(client -> {
                var patch = new AdminStatsPatchDTO(played, wins, lastPlayedAt, reason, dryRun);
                if (!dryRun && !root().confirmChange("statistics for user " + userId + " / " + game + " / " + mode,
                        client.admin().stats(userId), patch)) return 5;
                return ok(client.admin().patchStats(userId, game, mode, patch));
            });
        }
    }

    @Command(name = "stats", description = "Rebuild statistics from completed recordings.", subcommands = RebuildStats.class)
    static class Stats implements Runnable {
        @Spec CommandSpec spec;

        public void run() {
            spec.commandLine().usage(System.out);
        }
    }

    @Command(name = "rebuild", description = "Preview or replace aggregates derived from recorded history.")
    static class RebuildStats extends CliCommand {
        @Option(names = "--user", required = true) Long userId;
        @Option(names = "--game") String game;
        @Option(names = "--reason", required = true) String reason;
        @Option(names = "--dry-run") boolean dryRun;

        public Integer call() {
            return root().withClient(client -> {
                if (!dryRun && !root().confirmChange("statistics for user " + userId, client.admin().stats(userId),
                        "rebuild from completed " + (game == null ? "all" : game) + " recordings")) return 5;
                return ok(client.admin().rebuildStats(userId, game, reason, dryRun));
            });
        }
    }

    @Command(name = "overview", description = "Show account, session, game, lobby, and database totals.")
    static class Overview extends CliCommand {
        public Integer call() {
            return root().withClient(client -> ok(client.admin().overview()));
        }
    }

    @Command(name = "users", description = "Report users with allowlisted filters and sorting.")
    static class Users extends CliCommand {
        @Option(names = "--page", defaultValue = "0") int page;
        @Option(names = "--size", defaultValue = "25") int size;
        @Option(names = "--status") String status;
        @Option(names = "--role") String role;
        @Option(names = "--sort") String sort;
        @Option(names = "--direction") String direction;

        public Integer call() {
            return root().withClient(client -> ok(client.admin().reportUsers(page, size, status, role, sort, direction)));
        }
    }

    @Command(name = "games", description = "Report completed or incomplete recorded games.")
    static class Games extends CliCommand {
        @Option(names = "--page", defaultValue = "0") int page;
        @Option(names = "--size", defaultValue = "25") int size;
        @Option(names = "--game") String game;
        @Option(names = "--completed") Boolean completed;
        @Option(names = "--sort") String sort;
        @Option(names = "--direction") String direction;

        public Integer call() {
            return root().withClient(client -> ok(client.admin().games(page, size, game, completed, sort, direction)));
        }
    }

    @Command(name = "sessions", description = "Report session validity without credential material.")
    static class Sessions extends CliCommand {
        @Option(names = "--page", defaultValue = "0") int page;
        @Option(names = "--size", defaultValue = "25") int size;
        @Option(names = "--user") Long userId;
        @Option(names = "--valid") Boolean valid;
        @Option(names = "--sort") String sort;
        @Option(names = "--direction") String direction;

        public Integer call() {
            return root().withClient(client -> ok(client.admin().sessions(page, size, userId, valid, sort, direction)));
        }
    }
}
