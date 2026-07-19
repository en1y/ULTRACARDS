package com.ultracards.cli;

import com.ultracards.gateway.dto.admin.AdminRecordedGamePatchDTO;
import com.ultracards.gateway.dto.admin.AdminNotificationPatchDTO;
import com.ultracards.gateway.dto.admin.AdminStatsPatchDTO;
import com.ultracards.gateway.dto.admin.AdminUserPatchDTO;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Command(name = "db", description = "Run safe database edits, rebuilds, and reports.",
        subcommands = {DbCommands.Edit.class, DbCommands.Stats.class, DbCommands.Overview.class,
                DbCommands.Game.class, DbCommands.Users.class, DbCommands.Games.class, DbCommands.Sessions.class,
                DbCommands.Tokens.class, DbCommands.Notifications.class, DbCommands.DeleteGames.class})
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
        @Parameters(index = "0", paramLabel = "USER") String target;
        @Option(names = "--username") String username;
        @Option(names = "--email") String email;
        @Option(names = "--enabled") Boolean enabled;
        @Option(names = "--status") String status;
        @Option(names = "--reason", required = true) String reason;
        @Option(names = "--dry-run") boolean dryRun;

        public Integer call() {
            return root().withClient(client -> {
                var id = UserCommands.resolveUserId(target, page -> client.admin().users(page, 200));
                var patch = new AdminUserPatchDTO(username, email, enabled, status, reason, dryRun);
                if (!dryRun) {
                    var current = client.admin().user(id);
                    var proposed = client.admin().patchUser(id,
                            new AdminUserPatchDTO(username, email, enabled, status, reason, true));
                    if (!root().confirmChange("user " + id, current, proposed)) return 5;
                }
                return ok(client.admin().patchUser(id, patch));
            });
        }
    }

    @Command(name = "game-record", description = "Rename a recorded game without changing gameplay data.")
    static class EditGame extends CliCommand {
        @Parameters(index = "0") UUID id;
        @Option(names = "--name") String name;
        @Option(names = "--delete") boolean delete;
        @Option(names = "--reason", required = true) String reason;
        @Option(names = "--dry-run") boolean dryRun;

        public Integer call() {
            return root().withClient(client -> {
                if (delete) {
                    if (!root().confirmChange("recorded game " + id, client.admin().game(id), "delete")) return 5;
                    client.admin().deleteGame(id, reason);
                    return ok("Recorded game deleted");
                }
                if (name == null || name.isBlank()) throw new IllegalArgumentException("--name is required unless --delete is used");
                var patch = new AdminRecordedGamePatchDTO(name, reason, dryRun);
                if (!dryRun && !root().confirmChange("recorded game " + id, client.admin().game(id), patch)) return 5;
                return ok(client.admin().patchGame(id, patch));
            });
        }
    }

    @Command(name = "stats", description = "Override one per-mode statistics row with an audit reason.")
    static class EditStats extends CliCommand {
        @Option(names = "--user", required = true, paramLabel = "USER") String user;
        @Option(names = "--game", required = true) StatsGame game;
        @Option(names = "--mode", required = true) Mode mode;
        @Option(names = "--played", description = "Replace the played count; omit to preserve it.") Integer played;
        @Option(names = "--wins", description = "Replace the win count; omit to preserve it.") Integer wins;
        @Option(names = "--last-played-at") Instant lastPlayedAt;
        @Option(names = "--reason", required = true) String reason;
        @Option(names = "--dry-run") boolean dryRun;

        public Integer call() {
            return root().withClient(client -> {
                var userId = UserCommands.resolveUserId(user, page -> client.admin().users(page, 200));
                var patch = new AdminStatsPatchDTO(played, wins, lastPlayedAt, reason, dryRun);
                if (!dryRun && !root().confirmChange("statistics for user " + userId + " / " + game + " / " + mode,
                        client.admin().stats(userId), patch)) return 5;
                return ok(client.admin().patchStats(userId, game.name(), mode.name(), patch));
            });
        }
    }

    enum StatsGame { BRISKULA, TRESETA }

    enum Mode {
        TWO_PLAYERS,
        TWO_PLAYERS_FOUR_CARDS_IN_HAND_EACH,
        THREE_PLAYERS,
        FOUR_PLAYERS_NO_TEAMS,
        FOUR_PLAYERS_WITH_TEAMS
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
        @Option(names = "--user", required = true, paramLabel = "USER") String user;
        @Option(names = "--game") StatsGame game;
        @Option(names = "--reason", required = true) String reason;
        @Option(names = "--dry-run") boolean dryRun;

        public Integer call() {
            return root().withClient(client -> {
                var userId = UserCommands.resolveUserId(user, page -> client.admin().users(page, 200));
                if (!dryRun && !root().confirmChange("statistics for user " + userId, client.admin().stats(userId),
                        "rebuild from completed " + (game == null ? "all" : game) + " recordings")) return 5;
                return ok(client.admin().rebuildStats(userId, game == null ? null : game.name(), reason, dryRun));
            });
        }
    }

    @Command(name = "overview", description = "Show account, session, game, lobby, and database totals.")
    static class Overview extends CliCommand {
        public Integer call() {
            return root().withClient(client -> ok(client.admin().overview()));
        }
    }

    @Command(name = "game", aliases = "game-record", description = "Show one recorded game by UUID.")
    static class Game extends CliCommand {
        @Parameters(index = "0", paramLabel = "ID") UUID id;

        public Integer call() {
            return root().withClient(client -> ok(client.admin().game(id)));
        }
    }

    @Command(name = "users", aliases = "user-report", description = "Report users with allowlisted filters and sorting.")
    static class Users extends CliCommand {
        @Option(names = "--page", defaultValue = "0") int page;
        @Option(names = "--size", defaultValue = "25") int size;
        @Option(names = "--all", description = "Fetch every page from the selected starting page.") boolean all;
        @Option(names = "--status") String status;
        @Option(names = "--role") String role;
        @Option(names = "--query") String query;
        @Option(names = "--exact") Boolean exact;
        @Option(names = "--sort") String sort;
        @Option(names = "--direction") String direction;

        public Integer call() {
            return root().withClient(client -> ok(pages(page, size, all,
                    (page, size) -> client.admin().reportUsers(page, size, query, exact, status, role, sort, direction))));
        }
    }

    @Command(name = "games", aliases = "game-report", description = "Report completed or incomplete recorded games.")
    static class Games extends CliCommand {
        @Option(names = "--page", defaultValue = "0") int page;
        @Option(names = "--size", defaultValue = "25") int size;
        @Option(names = "--all", description = "Fetch every page from the selected starting page.") boolean all;
        @Option(names = "--game") String game;
        @Option(names = "--completed") Boolean completed;
        @Option(names = "--mode") String mode;
        @Option(names = "--query") String query;
        @Option(names = "--sort") String sort;
        @Option(names = "--direction") String direction;

        public Integer call() {
            return root().withClient(client -> ok(pages(page, size, all,
                    (page, size) -> client.admin().games(page, size, game, completed, mode, query, sort, direction))));
        }
    }

    @Command(name = "sessions", aliases = "session-report", description = "Report session validity without credential material.")
    static class Sessions extends CliCommand {
        @Option(names = "--page", defaultValue = "0") int page;
        @Option(names = "--size", defaultValue = "25") int size;
        @Option(names = "--all", description = "Fetch every page from the selected starting page.") boolean all;
        @Option(names = "--user", paramLabel = "USER") String user;
        @Option(names = "--id") UUID id;
        @Option(names = "--valid") Boolean valid;
        @Option(names = "--query") String query;
        @Option(names = "--sort") String sort;
        @Option(names = "--direction") String direction;

        public Integer call() {
            return root().withClient(client -> {
                var userId = user == null ? null : UserCommands.resolveUserId(user,
                        current -> client.admin().users(current, 200));
                return ok(pages(page, size, all,
                        (page, size) -> client.admin().sessions(page, size, id, userId, valid, query, sort, direction)));
            });
        }
    }

    @Command(name = "tokens", description = "Report session tokens without exposing credential material.")
    static class Tokens extends CliCommand {
        @Option(names = "--page", defaultValue = "0") int page;
        @Option(names = "--size", defaultValue = "25") int size;
        @Option(names = "--all") boolean all;
        @Option(names = "--id") UUID id;
        @Option(names = "--user") Long userId;
        @Option(names = "--active") Boolean active;

        public Integer call() {
            return root().withClient(client -> ok(pages(page, size, all,
                    (current, count) -> client.admin().tokens(current, count, id, userId, active))));
        }
    }

    @Command(name = "notifications", aliases = "notification", description = "Browse and edit persisted notifications.",
            subcommands = {Notifications.ListNotifications.class, Notifications.Edit.class, Notifications.Delete.class})
    static class Notifications implements Runnable {
        @Spec CommandSpec spec;
        public void run() { spec.commandLine().usage(System.out); }

        @Command(name = "list", aliases = "ls")
        static class ListNotifications extends CliCommand {
            @Option(names = "--page", defaultValue = "0") int page;
            @Option(names = "--size", defaultValue = "25") int size;
            @Option(names = "--all") boolean all;
            @Option(names = "--user") Long userId;
            @Option(names = "--type") String type;
            @Option(names = "--read") Boolean read;
            @Option(names = "--query") String query;

            public Integer call() {
                return root().withClient(client -> ok(pages(page, size, all,
                        (current, count) -> client.admin().notifications(current, count, userId, type, read, query))));
            }
        }

        @Command(name = "edit")
        static class Edit extends CliCommand {
            @Parameters(index = "0") UUID id;
            @Option(names = "--message") String message;
            @Option(names = "--read") Boolean read;
            @Option(names = "--reason", required = true) String reason;

            public Integer call() {
                if (message == null && read == null) throw new IllegalArgumentException("Provide --message or --read");
                return root().withClient(client -> ok(client.admin().patchNotification(id,
                        new AdminNotificationPatchDTO(message, read, reason))));
            }
        }

        @Command(name = "delete", aliases = {"rm", "remove"})
        static class Delete extends CliCommand {
            @Parameters(index = "0..*") List<UUID> ids;
            @Option(names = "--reason", required = true) String reason;

            public Integer call() {
                return root().withClient(client -> {
                    if (!root().confirm("Delete " + ids.size() + " notification(s)?")) return 5;
                    for (var id : ids) client.admin().deleteNotification(id, reason);
                    return ok("Notification(s) deleted");
                });
            }
        }
    }

    @Command(name = "delete-games", description = "Delete one or more recorded game histories.")
    static class DeleteGames extends CliCommand {
        @Parameters(index = "0..*", paramLabel = "ID") List<UUID> ids;
        @Option(names = "--reason", required = true) String reason;

        public Integer call() {
            return root().withClient(client -> {
                if (!root().confirm("Delete " + ids.size() + " recorded game(s)?")) return 5;
                for (var id : ids) client.admin().deleteGame(id, reason);
                return ok("Recorded game(s) deleted");
            });
        }
    }
}
