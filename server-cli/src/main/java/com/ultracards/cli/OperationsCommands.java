package com.ultracards.cli;

import com.ultracards.gateway.dto.admin.AdminNotificationRequestDTO;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

import java.util.UUID;

@Command(name = "system", description = "Check server and database health.",
        subcommands = {SystemCommands.Status.class, SystemCommands.Doctor.class})
class SystemCommands implements Runnable {
    @Spec CommandSpec spec;

    @Override public void run() {
        spec.commandLine().usage(System.out);
    }

    @Command(name = "status", description = "Show version, availability, and live object counts.")
    static class Status extends CliCommand {
        public Integer call() {
            return root().withClient(client -> ok(client.admin().status()));
        }
    }

    @Command(name = "doctor", description = "Fail when the server or database health check is unhealthy.")
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

@Command(name = "notify", description = "Send audited administrative notifications.",
        subcommands = {NotifyCommands.User.class, NotifyCommands.All.class})
class NotifyCommands implements Runnable {
    @Spec CommandSpec spec;

    @Override public void run() {
        spec.commandLine().usage(System.out);
    }

    @Command(name = "user", description = "Send a message to one user.")
    static class User extends CliCommand {
        @Parameters(index = "0") Long id;
        @Option(names = "--message", required = true) String message;
        @Option(names = "--reason", required = true) String reason;

        public Integer call() {
            return root().withClient(client ->
                    ok(client.admin().notifyUser(id, new AdminNotificationRequestDTO(message, reason))));
        }
    }

    @Command(name = "all", description = "Broadcast a message to every user after confirmation.")
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
        subcommands = {AuditCommands.ListEvents.class, AuditCommands.Show.class})
class AuditCommands implements Runnable {
    @Spec CommandSpec spec;

    @Override public void run() {
        spec.commandLine().usage(System.out);
    }

    @Command(name = "list", description = "List audit events newest first.")
    static class ListEvents extends CliCommand {
        @Option(names = "--page", defaultValue = "0") int page;
        @Option(names = "--size", defaultValue = "25") int size;

        public Integer call() {
            return root().withClient(client -> ok(client.admin().audit(page, size)));
        }
    }

    @Command(name = "show", description = "Show one audit event by UUID.")
    static class Show extends CliCommand {
        @Parameters(index = "0") UUID id;

        public Integer call() {
            return root().withClient(client -> ok(client.admin().audit(id)));
        }
    }
}
