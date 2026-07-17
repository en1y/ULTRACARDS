package com.ultracards.cli;

import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

import java.util.ArrayList;

@Command(name = "server", description = "Manage named server profiles.",
        subcommands = {ServerCommands.Add.class, ServerCommands.ListProfiles.class,
                ServerCommands.Use.class, ServerCommands.Remove.class})
class ServerCommands implements Runnable {
    @Spec CommandSpec spec;

    @Override public void run() {
        spec.commandLine().usage(System.out);
    }

    @Command(name = "add", description = "Save a server URL and select it when it is the first profile.")
    static class Add extends CliCommand {
        @Parameters(index = "0", paramLabel = "NAME") String name;
        @Parameters(index = "1", paramLabel = "URL") String url;

        public Integer call() {
            root().store.add(name, url);
            return ok("Saved server profile " + name);
        }
    }

    @Command(name = "list", description = "Show saved profiles and the active connection target.")
    static class ListProfiles extends CliCommand {
        public Integer call() {
            var profiles = new ArrayList<ServerProfile>();
            for (var entry : root().store.profiles().entrySet())
                profiles.add(new ServerProfile(entry.getKey().equals(root().store.activeProfile()) ? "●" : "",
                        entry.getKey(), entry.getValue()));
            return ok(profiles);
        }
    }

    @Command(name = "use", description = "Select the profile used by subsequent commands.")
    static class Use extends CliCommand {
        @Parameters(index = "0", paramLabel = "NAME") String name;

        public Integer call() {
            root().store.use(name);
            return ok("Using server profile " + name);
        }
    }

    @Command(name = "remove", description = "Remove a profile and its locally stored session.")
    static class Remove extends CliCommand {
        @Parameters(index = "0", paramLabel = "NAME") String name;

        public Integer call() {
            var current = root().store.profiles().get(name);
            if (current == null) throw new IllegalArgumentException("Unknown server profile: " + name);
            if (!root().confirmChange("server profile " + name, current, "remove profile and stored session")) return 5;
            root().store.remove(name);
            return ok("Removed server profile " + name);
        }
    }

    private record ServerProfile(String active, String name, String url) {}
}
