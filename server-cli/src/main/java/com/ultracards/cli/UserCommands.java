package com.ultracards.cli;

import com.ultracards.gateway.dto.admin.AdminPageDTO;
import com.ultracards.gateway.dto.admin.AdminUserSummaryDTO;
import com.ultracards.gateway.dto.admin.AdminUserPatchDTO;
import com.ultracards.gateway.dto.admin.AdminPointsPatchDTO;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

import java.util.ArrayList;
import java.util.function.IntFunction;

@Command(name = "user", aliases = "users", description = "Inspect accounts, roles, status, and sessions.",
        subcommands = {UserCommands.ListUsers.class, UserCommands.Show.class, UserCommands.Role.class,
                UserCommands.Stats.class, UserCommands.Points.class, UserCommands.Spending.class,
                UserCommands.PointsChart.class, UserCommands.Enable.class, UserCommands.Disable.class, UserCommands.Sessions.class})
class UserCommands implements Runnable {
    @Spec CommandSpec spec;

    @Override public void run() {
        spec.commandLine().usage(System.out);
    }

    @Command(name = "list", aliases = "ls", description = "List and filter users with server-side pagination.")
    static class ListUsers extends CliCommand {
        @Option(names = "--page", defaultValue = "0") int page;
        @Option(names = "--size", defaultValue = "25") int size;
        @Option(names = "--all", description = "Fetch every page from the selected starting page.") boolean all;
        @Option(names = "--status") String status;
        @Option(names = "--role") String role;
        @Option(names = "--sort") String sort;
        @Option(names = "--direction") String direction;

        public Integer call() {
            return root().withClient(client -> ok(pages(page, size, all,
                    (page, size) -> client.admin().reportUsers(page, size, null, null, status, role, sort, direction))));
        }
    }

    @Command(name = "show", aliases = "get", description = "Show one user by ID, email, or username.")
    static class Show extends CliCommand {
        @Parameters(index = "0", paramLabel = "USER") String target;

        public Integer call() {
            return root().withClient(client -> ok(client.admin().user(resolveUserId(target,
                    page -> client.admin().users(page, 200)))));
        }
    }

    @Command(name = "stats", description = "Show game statistics for a user by ID, email, or username.")
    static class Stats extends CliCommand {
        @Parameters(index = "0", paramLabel = "USER") String target;

        public Integer call() {
            return root().withClient(client -> ok(client.admin().stats(resolveUserId(target,
                    page -> client.admin().users(page, 200)))));
        }
    }

    @Command(name = "points", description = "Add or remove Points with an audited reason.")
    static class Points extends CliCommand {
        @Parameters(index = "0", paramLabel = "USER") String target;
        @Option(names = "--amount", required = true) long amount;
        @Option(names = "--reason", required = true) String reason;
        @Option(names = "--dry-run", description = "Preview the resulting balance without changing it.") boolean dryRun;

        public Integer call() {
            return root().withClient(client -> {
                var id = resolveUserId(target, page -> client.admin().users(page, 200));
                var current = client.admin().user(id);
                if (!dryRun && !root().confirmChange("Points for user " + id,
                        current.points() + "P", (amount >= 0 ? "+" : "") + amount + "P")) return 5;
                return ok(client.admin().adjustPoints(id, new AdminPointsPatchDTO(amount, reason, dryRun)));
            });
        }
    }

    @Command(name = "spending", aliases = {"points-history", "ledger"},
            description = "Show where a user's Points went, newest first.")
    static class Spending extends CliCommand {
        @Parameters(index = "0", paramLabel = "USER") String target;
        @Option(names = "--page", description = "Zero-based page index.") int page;
        @Option(names = "--size", description = "Entries per page (default 25).") int size = 25;

        public Integer call() {
            return root().withClient(client -> {
                var id = resolveUserId(target, p -> client.admin().users(p, 200));
                return ok(client.admin().pointsLedger(id, page, size).items());
            });
        }
    }

    @Command(name = "points-chart", aliases = "chart",
            description = "Draw a user's Points balance over time.")
    static class PointsChart extends CliCommand {
        @Parameters(index = "0", paramLabel = "USER") String target;
        @Option(names = "--days", description = "Days to cover; 0 is the whole ledger (default 30).") int days = 30;

        public Integer call() {
            return root().withClient(client -> {
                var id = resolveUserId(target, p -> client.admin().users(p, 200));
                return ok(client.admin().pointsSeries(id, Math.max(0, days)));
            });
        }
    }

    @Command(name = "role", description = "Grant or revoke administrator and moderator roles.",
            subcommands = {GrantRole.class, RevokeRole.class})
    static class Role implements Runnable {
        @Spec CommandSpec spec;

        public void run() {
            spec.commandLine().usage(System.out);
        }
    }

    @Command(name = "grant", description = "Grant ADMIN or MODERATOR to a user.")
    static class GrantRole extends CliCommand {
        @Parameters(index = "0", paramLabel = "USER") String target;
        @Parameters(index = "1") String role;
        @Option(names = "--reason", required = true) String reason;

        public Integer call() {
            return root().withClient(client -> ok(client.admin().grantRole(resolveUserId(target,
                    page -> client.admin().users(page, 200)), role, reason)));
        }
    }

    @Command(name = "revoke", description = "Revoke ADMIN or MODERATOR from a user.")
    static class RevokeRole extends CliCommand {
        @Parameters(index = "0", paramLabel = "USER") String target;
        @Parameters(index = "1") String role;
        @Option(names = "--reason", required = true) String reason;

        public Integer call() {
            return root().withClient(client -> {
                var id = resolveUserId(target, page -> client.admin().users(page, 200));
                var current = client.admin().user(id);
                if (!root().confirmChange("user " + id, current.roles(), "remove role " + role)) return 5;
                return ok(client.admin().revokeRole(id, role, reason));
            });
        }
    }

    abstract static class UserState extends CliCommand {
        @Parameters(index = "0", paramLabel = "USER") String target;
        @Option(names = "--reason", required = true) String reason;

        abstract boolean enabled();

        public Integer call() {
            var patch = new AdminUserPatchDTO(null, null, enabled(), null, enabled() ? "ACTIVE" : "DISABLED", reason, false);
            return root().withClient(client -> {
                var id = resolveUserId(target, page -> client.admin().users(page, 200));
                var current = client.admin().user(id);
                if (!enabled() && !root().confirmChange("user " + id,
                        "enabled=" + current.enabled() + ", status=" + current.status(),
                        "enabled=false, status=DISABLED, revoke sessions")) return 5;
                return ok(client.admin().patchUser(id, patch));
            });
        }
    }

    @Command(name = "enable", description = "Enable an account and set it ACTIVE.")
    static class Enable extends UserState {
        boolean enabled() { return true; }
    }

    @Command(name = "disable", description = "Disable an account and revoke its sessions.")
    static class Disable extends UserState {
        boolean enabled() { return false; }
    }

    @Command(name = "sessions", description = "Manage a user's login sessions.", subcommands = RevokeSessions.class)
    static class Sessions implements Runnable {
        @Spec CommandSpec spec;

        public void run() {
            spec.commandLine().usage(System.out);
        }
    }

    @Command(name = "revoke", description = "Revoke every active session for a user.")
    static class RevokeSessions extends CliCommand {
        @Parameters(index = "0", paramLabel = "USER") String target;
        @Option(names = "--reason", required = true) String reason;

        public Integer call() {
            return root().withClient(client -> {
                var id = resolveUserId(target, page -> client.admin().users(page, 200));
                var current = client.admin().user(id);
                if (!root().confirmChange("user " + id, current, "revoke all sessions")) return 5;
                client.admin().revokeSessions(id, reason);
                return ok("Sessions revoked");
            });
        }
    }

    static long resolveUserId(String target, IntFunction<AdminPageDTO<AdminUserSummaryDTO>> pages) {
        try {
            return Long.parseLong(target);
        } catch (NumberFormatException ignored) {
        }
        if (target == null || target.isBlank()) throw new IllegalArgumentException("User cannot be blank");

        // ponytail: exact paged lookup avoids a new endpoint; add server-side search if user count makes this slow.
        var matches = new ArrayList<Long>();
        var pageNumber = 0;
        while (true) {
            var page = pages.apply(pageNumber);
            for (var user : page.items()) {
                if (target.equalsIgnoreCase(user.email()) || target.equalsIgnoreCase(user.username()))
                    matches.add(user.id());
            }
            if (page.page() + 1 >= page.totalPages()) break;
            pageNumber = page.page() + 1;
        }
        if (matches.isEmpty()) throw new UltracardsAdminCli.CliStateException("No user found for: " + target);
        if (matches.size() > 1) throw new UltracardsAdminCli.CliStateException(
                "Multiple users match '" + target + "'; use a numeric ID");
        return matches.getFirst();
    }
}
