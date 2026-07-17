package com.ultracards.cli;

import com.ultracards.gateway.dto.admin.AdminUserPatchDTO;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

@Command(name = "user", aliases = "users", description = "Inspect accounts, roles, status, and sessions.",
        subcommands = {UserCommands.ListUsers.class, UserCommands.Show.class, UserCommands.Role.class,
                UserCommands.Enable.class, UserCommands.Disable.class, UserCommands.Sessions.class})
class UserCommands implements Runnable {
    @Spec CommandSpec spec;

    @Override public void run() {
        spec.commandLine().usage(System.out);
    }

    @Command(name = "list", description = "List users with server-side pagination.")
    static class ListUsers extends CliCommand {
        @Option(names = "--page", defaultValue = "0") int page;
        @Option(names = "--size", defaultValue = "25") int size;

        public Integer call() {
            return root().withClient(client -> ok(client.admin().users(page, size)));
        }
    }

    @Command(name = "show", description = "Show one user by numeric ID.")
    static class Show extends CliCommand {
        @Parameters(index = "0") Long id;

        public Integer call() {
            return root().withClient(client -> ok(client.admin().user(id)));
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
        @Parameters(index = "0") Long id;
        @Parameters(index = "1") String role;
        @Option(names = "--reason", required = true) String reason;

        public Integer call() {
            return root().withClient(client -> ok(client.admin().grantRole(id, role, reason)));
        }
    }

    @Command(name = "revoke", description = "Revoke ADMIN or MODERATOR from a user.")
    static class RevokeRole extends CliCommand {
        @Parameters(index = "0") Long id;
        @Parameters(index = "1") String role;
        @Option(names = "--reason", required = true) String reason;

        public Integer call() {
            return root().withClient(client -> {
                var current = client.admin().user(id);
                if (!root().confirmChange("user " + id, current.roles(), "remove role " + role)) return 5;
                return ok(client.admin().revokeRole(id, role, reason));
            });
        }
    }

    abstract static class UserState extends CliCommand {
        @Parameters(index = "0") Long id;
        @Option(names = "--reason", required = true) String reason;

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
        @Parameters(index = "0") Long id;
        @Option(names = "--reason", required = true) String reason;

        public Integer call() {
            return root().withClient(client -> {
                var current = client.admin().user(id);
                if (!root().confirmChange("user " + id, current, "revoke all sessions")) return 5;
                client.admin().revokeSessions(id, reason);
                return ok("Sessions revoked");
            });
        }
    }
}
