package com.ultracards.cli;

import com.ultracards.gateway.dto.admin.AdminPointEventPatchDTO;
import com.ultracards.gateway.dto.admin.AdminPointsSettingsPatchDTO;
import com.ultracards.gateway.dto.admin.AdminWagerFeePatchDTO;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;
import picocli.CommandLine.Model.CommandSpec;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Command(name = "economy", aliases = "points-economy", description = "Manage bet fees and scheduled Point events.",
        subcommands = {EconomyCommands.Fee.class, EconomyCommands.Events.class})
class EconomyCommands implements Runnable {
    @Spec CommandSpec spec;

    @Override public void run() { spec.commandLine().usage(System.out); }

    @Command(name = "fee", description = "Show or update the fee charged on losing bet stakes.")
    static class Fee extends CliCommand {
        @Option(names = "--set", description = "New whole percentage from 0 to 100.") Integer percent;
        @Option(names = "--game", description = "Limit the fee to one game; omit for the default that every game falls back to.") String game;
        @Option(names = "--mode", description = "Limit the fee to one mode of --game.") String mode;
        @Option(names = "--reset", description = "Drop the --game (and --mode) fee so it inherits again.") boolean reset;
        @Option(names = "--reason", description = "Audited reason; required with --set or --reset.") String reason;

        public Integer call() {
            if (mode != null && game == null) throw new IllegalArgumentException("--mode needs --game");
            if (reset && game == null) throw new IllegalArgumentException("--reset needs --game");
            if (reset && percent != null) throw new IllegalArgumentException("--reset cannot be combined with --set");
            var target = game == null ? "bet fee" : "bet fee for " + game + (mode == null ? "" : " " + mode);
            return root().withClient(client -> {
                if (reset) {
                    requireReason();
                    if (!root().confirmChange(target, "explicit", "inherited")) return 5;
                    return ok(client.admin().resetWagerFee(game, mode, reason));
                }
                if (percent == null) return ok(game == null ? client.admin().pointsSettings() : client.admin().wagerFees());
                requireReason();
                if (!root().confirmChange(target, currentFee(client.admin(), game, mode), percent + "%")) return 5;
                if (game == null) return ok(client.admin().updatePointsSettings(new AdminPointsSettingsPatchDTO(percent, reason)));
                return ok(client.admin().updateWagerFee(game, new AdminWagerFeePatchDTO(mode, percent, reason)));
            });
        }

        private void requireReason() {
            if (reason == null || reason.isBlank()) throw new IllegalArgumentException("--reason is required with --set or --reset");
        }

        private static String currentFee(com.ultracards.gateway.service.AdminService admin, String game, String mode) {
            if (game == null) return admin.pointsSettings().wagerFeePercent() + "%";
            for (var fee : admin.wagerFees())
                if (fee.game().equalsIgnoreCase(game) && java.util.Objects.equals(fee.mode(), mode == null ? null : mode.toUpperCase()))
                    return fee.feePercent() + "%" + (fee.inherited() ? " (inherited)" : "");
            return "unknown";
        }
    }

    @Command(name = "event", aliases = "events", description = "List, create, update, enable, or disable Point events.",
            subcommands = {ListEvents.class, SaveEvent.class, ToggleEvent.class, DeleteEvent.class})
    static class Events implements Runnable {
        @Spec CommandSpec spec;

        @Override public void run() { spec.commandLine().usage(System.out); }
    }

    @Command(name = "list", aliases = "ls", description = "List every configured event.")
    static class ListEvents extends CliCommand {
        public Integer call() { return root().withClient(client -> ok(client.admin().pointEvents())); }
    }

    @Command(name = "save", description = "Create an event, or replace one when --id is supplied.")
    static class SaveEvent extends CliCommand {
        @Option(names = "--id", description = "Existing event UUID. Omit to create.") UUID id;
        @Option(names = "--name", required = true) String name;
        @Option(names = "--description", defaultValue = "") String description;
        @Option(names = "--starts", required = true, description = "ISO-8601 instant, for example 2026-08-15T18:00:00Z.") Instant starts;
        @Option(names = "--ends", description = "Optional ISO-8601 instant.") Instant ends;
        @Option(names = "--game", split = ",", description = "Eligible game types. Repeat or comma-separate; omit for all.") List<String> games = new ArrayList<>();
        @Option(names = "--completion-reward", defaultValue = "0") long completionReward;
        @Option(names = "--disabled", description = "Save without making the event visible or rewardable.") boolean disabled;
        @Option(names = "--achievement", description = "Repeatable NAME|REWARD|GAMES|WINS|LOSSES|DRAWS|GAME_TYPES|HIDDEN|DESCRIPTION goal.") List<String> achievementSpecs = new ArrayList<>();
        @Option(names = "--reason", required = true) String reason;

        public Integer call() {
            if (achievementSpecs.isEmpty()) throw new IllegalArgumentException("At least one --achievement is required");
            var achievements = new ArrayList<AdminPointEventPatchDTO.Achievement>();
            for (var value : achievementSpecs) achievements.add(parseAchievement(value));
            var patch = new AdminPointEventPatchDTO(name, description, starts, ends, games, completionReward,
                    !disabled, achievements, reason);
            return root().withClient(client -> {
                if (!root().confirmChange(id == null ? "new Point event" : "Point event " + id,
                        id == null ? "not created" : "existing configuration", name)) return 5;
                return ok(id == null ? client.admin().createPointEvent(patch) : client.admin().updatePointEvent(id, patch));
            });
        }

        static AdminPointEventPatchDTO.Achievement parseAchievement(String value) {
            var fields = value == null ? new String[0] : value.split("\\|", -1);
            if (fields.length < 8 || fields.length > 9 || fields[0].isBlank())
                throw new IllegalArgumentException("Achievement must be NAME|REWARD|GAMES|WINS|LOSSES|DRAWS|GAME_TYPES|HIDDEN|DESCRIPTION");
            var gameTypes = fields[6].isBlank() ? List.<String>of()
                    : List.of(fields[6].trim().toUpperCase().split(","));
            if (!fields[7].equalsIgnoreCase("true") && !fields[7].equalsIgnoreCase("false"))
                throw new IllegalArgumentException("hidden must be true or false");
            return new AdminPointEventPatchDTO.Achievement(null, fields[0].trim(), fields.length == 9 ? fields[8].trim() : "",
                    number(fields[2], "games"), number(fields[3], "wins"), number(fields[4], "losses"),
                    number(fields[5], "draws"), points(fields[1], "reward"), gameTypes,
                    Boolean.parseBoolean(fields[7]));
        }

        private static int number(String value, String label) {
            try {
                var number = Integer.parseInt(value.isBlank() ? "0" : value.trim());
                if (number < 0) throw new NumberFormatException();
                return number;
            } catch (NumberFormatException error) {
                throw new IllegalArgumentException(label + " must be a non-negative whole number");
            }
        }

        private static long points(String value, String label) {
            try {
                var number = Long.parseLong(value.isBlank() ? "0" : value.trim());
                if (number < 0) throw new NumberFormatException();
                return number;
            } catch (NumberFormatException error) {
                throw new IllegalArgumentException(label + " must be a non-negative whole number");
            }
        }
    }

    @Command(name = "enable", aliases = {"disable", "toggle"}, description = "Enable or disable an event.")
    static class ToggleEvent extends CliCommand {
        @Parameters(index = "0", paramLabel = "EVENT_ID") UUID id;
        @Option(names = "--enabled", required = true) boolean enabled;
        @Option(names = "--reason", required = true) String reason;

        public Integer call() {
            return root().withClient(client -> {
                if (!root().confirmChange("Point event " + id, "enabled=" + !enabled, "enabled=" + enabled)) return 5;
                return ok(client.admin().setPointEventEnabled(id, enabled, reason));
            });
        }
    }

    @Command(name = "delete", aliases = {"rm", "remove"}, description = "Delete an event and its sub-achievements.")
    static class DeleteEvent extends CliCommand {
        @Parameters(index = "0", paramLabel = "EVENT_ID") UUID id;
        @Option(names = "--reason", required = true) String reason;

        public Integer call() {
            return root().withClient(client -> {
                if (!root().confirm("Delete Point event " + id + "? Earned rewards remain in the ledger.")) return 5;
                client.admin().deletePointEvent(id, reason);
                return ok("Point event deleted");
            });
        }
    }
}
