package com.ultracards.cli;

import com.ultracards.gateway.dto.admin.AdminNotificationRequestDTO;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

import java.util.UUID;
import java.util.List;

@Command(name = "overview", aliases = {"dashboard", "summary"},
        description = "Show the server dashboard: version, health, users, sessions, games, lobbies, and database totals.")
class OverviewCommands extends CliCommand {
    public Integer call() {
        return root().withClient(client -> ok(client.admin().dashboard()));
    }
}

@Command(name = "system", description = "Check server and database health.",
        subcommands = {SystemCommands.Status.class, SystemCommands.Doctor.class})
class SystemCommands implements Runnable {
    @Spec CommandSpec spec;

    @Override public void run() {
        spec.commandLine().usage(System.out);
    }

    @Command(name = "status", aliases = "health", description = "Show version, availability, and live object counts.")
    static class Status extends CliCommand {
        public Integer call() {
            return root().withClient(client -> ok(client.admin().status()));
        }
    }

    @Command(name = "doctor", aliases = "check", description = "Fail when the server or database health check is unhealthy.")
    static class Doctor extends CliCommand {
        public Integer call() {
            return root().withClient(client -> {
                var status = client.admin().status();
                if (!status.databaseAvailable()) throw new IllegalStateException("Database health check failed");
                return ok(status);
            });
        }
    }
}

@Command(name = "session", aliases = "sessions", description = "Expire or delete one login session.",
        subcommands = {SessionCommands.Expire.class, SessionCommands.Delete.class})
class SessionCommands implements Runnable {
    @Spec CommandSpec spec;
    public void run() { spec.commandLine().usage(System.out); }

    abstract static class Change extends CliCommand {
        @Parameters(index = "0..*", paramLabel = "SESSION") List<UUID> ids;
        @Option(names = "--reason", required = true) String reason;
        abstract void apply(com.ultracards.gateway.service.AdminService admin, UUID id, String reason);
        abstract String action();

        public Integer call() {
            return root().withClient(client -> {
                if (!root().confirm("" + action() + " " + ids.size() + " session(s)?")) return 5;
                for (var id : ids) apply(client.admin(), id, reason);
                return ok("Session(s) " + action().toLowerCase());
            });
        }
    }

    @Command(name = "expire", description = "Mark a session expired while retaining its record.")
    static class Expire extends Change {
        void apply(com.ultracards.gateway.service.AdminService admin, UUID id, String reason) { admin.expireSession(id, reason); }
        String action() { return "Expire"; }
    }

    @Command(name = "delete", aliases = {"rm", "remove"}, description = "Delete a session record.")
    static class Delete extends Change {
        void apply(com.ultracards.gateway.service.AdminService admin, UUID id, String reason) { admin.deleteSession(id, reason); }
        String action() { return "Delete"; }
    }
}

@Command(name = "notify", description = "Send audited administrative notifications.",
        subcommands = {NotifyCommands.User.class, NotifyCommands.All.class})
class NotifyCommands implements Runnable {
    @Spec CommandSpec spec;

    @Override public void run() {
        spec.commandLine().usage(System.out);
    }

    @Command(name = "user", description = "Send a message to one user.")
    static class User extends CliCommand {
        @Parameters(index = "0", paramLabel = "USER") String target;
        @Option(names = "--message", required = true) String message;
        @Option(names = "--reason", required = true) String reason;

        public Integer call() {
            return root().withClient(client -> ok(client.admin().notifyUser(
                    UserCommands.resolveUserId(target, page -> client.admin().users(page, 200)),
                    new AdminNotificationRequestDTO(message, reason))));
        }
    }

    @Command(name = "all", aliases = "broadcast", description = "Broadcast a message to every user after confirmation.")
    static class All extends CliCommand {
        @Option(names = "--message", required = true) String message;
        @Option(names = "--reason", required = true) String reason;

        public Integer call() {
            if (!root().confirm("Send this notification to every user?")) return 5;
            return root().withClient(client -> ok(client.admin().notifyAll(new AdminNotificationRequestDTO(message, reason))));
        }
    }
}

@Command(name = "audit", description = "Inspect immutable administrative audit history.",
        subcommands = {AuditCommands.ListEvents.class, AuditCommands.Show.class, AuditCommands.Undo.class})
class AuditCommands implements Runnable {
    @Spec CommandSpec spec;

    @Override public void run() {
        spec.commandLine().usage(System.out);
    }

    @Command(name = "list", aliases = "ls", description = "List audit events newest first.")
    static class ListEvents extends CliCommand {
        @Option(names = "--page", defaultValue = "0") int page;
        @Option(names = "--size", defaultValue = "25") int size;
        @Option(names = "--all", description = "Fetch every page from the selected starting page.") boolean all;
        @Option(names = "--target-type") String targetType;
        @Option(names = "--target-id") String targetId;

        public Integer call() {
            return root().withClient(client -> ok(pages(page, size, all,
                    (current, count) -> client.admin().audit(current, count, targetType, targetId))));
        }
    }

    @Command(name = "show", aliases = "get", description = "Show one audit event by UUID.")
    static class Show extends CliCommand {
        @Parameters(index = "0") UUID id;

        public Integer call() {
            return root().withClient(client -> ok(client.admin().audit(id)));
        }
    }

    @Command(name = "undo", description = "Undo a reversible administrative action.")
    static class Undo extends CliCommand {
        @Parameters(index = "0") UUID id;
        @Option(names = "--reason", required = true) String reason;

        public Integer call() {
            return root().withClient(client -> {
                if (!root().confirm("Undo audit event " + id + "?")) return 5;
                client.admin().undoAudit(id, reason);
                return ok("Audit action undone");
            });
        }
    }
}
