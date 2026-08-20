package com.ultracards.cli;

import com.ultracards.gateway.dto.admin.AdminAchievementDTO;
import com.ultracards.gateway.dto.admin.AdminAchievementsPatchDTO;
import com.ultracards.gateway.dto.admin.AdminDailyGoalDTO;
import com.ultracards.gateway.dto.admin.AdminDailyGoalsPatchDTO;
import com.ultracards.gateway.dto.admin.AdminPointEventPatchDTO;
import com.ultracards.gateway.dto.admin.AdminStreakPatchDTO;
import com.ultracards.gateway.dto.admin.AdminPointsSettingsPatchDTO;
import com.ultracards.gateway.dto.admin.AdminWagerFeePatchDTO;
import com.ultracards.gateway.dto.points.PointsStreakDTO;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;
import picocli.CommandLine.Model.CommandSpec;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Command(name = "economy", aliases = "points-economy",
        description = "Manage Points grants, bet fees, achievements, streaks, and scheduled events.",
        subcommands = {EconomyCommands.Settings.class, EconomyCommands.Fee.class, EconomyCommands.Events.class,
                EconomyCommands.Achievements.class, EconomyCommands.Streak.class,
                EconomyCommands.DailyGoals.class})
class EconomyCommands implements Runnable {
    @Spec CommandSpec spec;

    @Override public void run() { spec.commandLine().usage(System.out); }

    @Command(name = "settings", aliases = "rules",
            description = "Show or update the Points granted to new accounts and by daily claims.")
    static class Settings extends CliCommand {
        @Option(names = {"--new-account-points", "--starting-balance"}, paramLabel = "POINTS",
                description = "Points granted when a new account is initialized.") Long startingBalance;
        @Option(names = {"--daily-points", "--daily-reward"}, paramLabel = "POINTS",
                description = "Points awarded by an eligible daily claim.") Long dailyReward;
        @Option(names = "--reason", description = "Audited reason; required when changing a value.") String reason;

        public Integer call() {
            if (startingBalance == null && dailyReward == null)
                return root().withClient(client -> ok(client.admin().pointsSettings()));
            if (reason == null || reason.isBlank())
                throw new IllegalArgumentException("--reason is required when changing Points settings");
            if (startingBalance != null && startingBalance < 0)
                throw new IllegalArgumentException("--new-account-points cannot be negative");
            if (dailyReward != null && dailyReward <= 0)
                throw new IllegalArgumentException("--daily-points must be above 0");
            return root().withClient(client -> {
                var current = client.admin().pointsSettings();
                var proposedStart = startingBalance == null ? current.startingBalance() : startingBalance;
                var proposedDaily = dailyReward == null ? current.dailyReward() : dailyReward;
                if (!root().confirmChange("Points account rules", summary(current.startingBalance(), current.dailyReward()),
                        summary(proposedStart, proposedDaily))) return 5;
                return ok(client.admin().updatePointsSettings(
                        new AdminPointsSettingsPatchDTO(null, startingBalance, dailyReward, reason)));
            });
        }

        private static String summary(long starting, long daily) {
            return starting + " P for new accounts; " + daily + " P per daily claim";
        }
    }

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
                if (game == null) return ok(client.admin().updatePointsSettings(
                        new AdminPointsSettingsPatchDTO(percent, null, null, reason)));
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

    @Command(name = "achievement", aliases = "achievements",
            description = "List, add, edit, or remove the one-time streak and lifetime achievements.",
            subcommands = {ListAchievements.class, SaveAchievement.class, DeleteAchievement.class,
                    AchievementHolders.class})
    static class Achievements implements Runnable {
        @Spec CommandSpec spec;

        @Override public void run() { spec.commandLine().usage(System.out); }
    }

    @Command(name = "list", aliases = "ls", description = "List every configured achievement.")
    static class ListAchievements extends CliCommand {
        public Integer call() { return root().withClient(client -> ok(client.admin().achievements())); }
    }

    @Command(name = "save", aliases = {"add", "set"},
            description = "Add an achievement, or edit the one already using --code.")
    static class SaveAchievement extends CliCommand {
        @Option(names = "--code", description = "Existing achievement code. Omit to add a new one.") String code;
        @Option(names = "--name", description = "Shown to players. Required when adding.") String name;
        @Option(names = "--metric", description = "STREAK, GAMES, or WINS. Required when adding.") String metric;
        @Option(names = "--target", description = "Streak days, or lifetime games/wins. Required when adding.") Long target;
        @Option(names = {"--reward", "--reward-points"}, description = "Points paid once, when it is earned.") Long reward;
        @Option(names = "--enabled", negatable = true, description = "Hide it from players without deleting it.") Boolean enabled;
        @Option(names = "--reason", required = true) String reason;

        public Integer call() {
            return root().withClient(client -> {
                // The API replaces the whole list, so read it, change one row, and send it back.
                var achievements = new ArrayList<>(client.admin().achievements());
                var index = indexOf(achievements, code);
                if (code != null && index < 0) throw new IllegalArgumentException("No achievement with code: " + code);
                var existing = index < 0 ? null : achievements.get(index);
                if (existing == null && (name == null || metric == null || target == null))
                    throw new IllegalArgumentException("--name, --metric, and --target are required when adding");
                var updated = new AdminAchievementDTO(
                        existing == null ? null : existing.code(),
                        name != null ? name : existing.name(),
                        metric != null ? metric.toUpperCase() : existing.metric(),
                        target != null ? target : existing.target(),
                        reward != null ? reward : existing == null ? 0L : existing.rewardPoints(),
                        enabled != null ? enabled : existing == null || existing.enabled());
                if (updated.target() != null && updated.target() <= 0)
                    throw new IllegalArgumentException("--target must be above 0");
                if (updated.rewardPoints() != null && updated.rewardPoints() < 0)
                    throw new IllegalArgumentException("--reward cannot be negative");
                if (!root().confirmChange(existing == null ? "new achievement" : "achievement " + code,
                        existing == null ? "not configured" : summary(existing), summary(updated))) return 5;
                if (index < 0) achievements.add(updated); else achievements.set(index, updated);
                return ok(client.admin().updateAchievements(new AdminAchievementsPatchDTO(achievements, reason)));
            });
        }

        private static int indexOf(List<AdminAchievementDTO> achievements, String code) {
            if (code == null) return -1;
            for (int index = 0; index < achievements.size(); index++)
                if (code.equals(achievements.get(index).code())) return index;
            return -1;
        }

        private static String summary(AdminAchievementDTO achievement) {
            return "%s: %s %d for %dP%s".formatted(achievement.name(), achievement.metric(),
                    achievement.target(), achievement.rewardPoints() == null ? 0 : achievement.rewardPoints(),
                    achievement.enabled() != null && !achievement.enabled() ? " (disabled)" : "");
        }
    }

    @Command(name = "delete", aliases = {"rm", "remove"}, description = "Remove an achievement.")
    static class DeleteAchievement extends CliCommand {
        @Parameters(index = "0", paramLabel = "CODE") String code;
        @Option(names = "--reason", required = true) String reason;

        public Integer call() {
            return root().withClient(client -> {
                var remaining = new ArrayList<AdminAchievementDTO>();
                var found = false;
                for (var achievement : client.admin().achievements()) {
                    if (code.equals(achievement.code())) found = true; else remaining.add(achievement);
                }
                if (!found) throw new IllegalArgumentException("No achievement with code: " + code);
                if (!root().confirm("Delete achievement " + code + "? Players keep what they already earned."))
                    return 5;
                return ok(client.admin().updateAchievements(new AdminAchievementsPatchDTO(remaining, reason)));
            });
        }
    }

    @Command(name = "holders", aliases = {"who", "earned-by"},
            description = "List the players who earned an achievement.")
    static class AchievementHolders extends CliCommand {
        @Parameters(index = "0", paramLabel = "CODE") String code;
        @Option(names = "--page", defaultValue = "0") int page;
        @Option(names = "--size", defaultValue = "25") int size;
        @Option(names = "--all", description = "Fetch every page instead of just --page.") boolean all;

        public Integer call() {
            return root().withClient(client ->
                    ok(pages(page, size, all, (current, pageSize) ->
                            client.admin().achievementHolders(code, current, pageSize))));
        }
    }

    @Command(name = "streak", description = "Show or correct a player's play streak and freezes.")
    static class Streak extends CliCommand {
        @Parameters(index = "0", paramLabel = "USER", description = "Numeric ID, username, or email.") String user;
        @Option(names = "--freezes", description = "Set unspent streak freezes, from 0 to 3.") Integer freezes;
        @Option(names = "--current", description = "Set the running streak length in days.") Integer current;
        @Option(names = "--longest", description = "Set the longest streak on record.") Integer longest;
        @Option(names = "--last-played", paramLabel = "YYYY-MM-DD",
                description = "Set the day the streak last advanced.") LocalDate lastPlayed;
        @Option(names = "--reset", description = "Clear the streak, its record, and every freeze.") boolean reset;
        @Option(names = "--reason", description = "Audited reason; required for any change.") String reason;

        public Integer call() {
            var changing = freezes != null || current != null || longest != null || lastPlayed != null || reset;
            if (reset && (freezes != null || current != null || longest != null || lastPlayed != null))
                throw new IllegalArgumentException("--reset cannot be combined with other changes");
            if (freezes != null && (freezes < 0 || freezes > 3))
                throw new IllegalArgumentException("--freezes must be between 0 and 3");
            if (changing && (reason == null || reason.isBlank()))
                throw new IllegalArgumentException("--reason is required when changing a streak");
            return root().withClient(client -> {
                var id = UserCommands.resolveUserId(user, page -> client.admin().users(page, 100));
                if (!changing) return ok(client.admin().streak(id));
                var before = client.admin().streak(id);
                if (reset) {
                    if (!root().confirmChange("streak for user " + id, summary(before), "cleared")) return 5;
                    return ok(client.admin().resetStreak(id, reason));
                }
                var patch = new AdminStreakPatchDTO(current, longest, lastPlayed, freezes, reason);
                if (!root().confirmChange("streak for user " + id, summary(before), proposed(before, patch)))
                    return 5;
                return ok(client.admin().updateStreak(id, patch));
            });
        }

        private static String summary(PointsStreakDTO streak) {
            return "%d day streak, longest %d, %d freezes, last played %s".formatted(streak.current(),
                    streak.longest(), streak.freezes(),
                    streak.lastPlayedDate() == null ? "never" : streak.lastPlayedDate());
        }

        private static String proposed(PointsStreakDTO before, AdminStreakPatchDTO patch) {
            return "%d day streak, longest %d, %d freezes, last played %s".formatted(
                    patch.currentStreak() == null ? before.current() : patch.currentStreak(),
                    patch.longestStreak() == null ? before.longest() : patch.longestStreak(),
                    patch.freezes() == null ? before.freezes() : patch.freezes(),
                    patch.lastPlayedDate() == null
                            ? (before.lastPlayedDate() == null ? "never" : before.lastPlayedDate())
                            : patch.lastPlayedDate());
        }
    }

    @Command(name = "daily-goal", aliases = {"daily-goals", "daily"},
            description = "List, add, edit, or remove the goals that reset every day.",
            subcommands = {ListDailyGoals.class, SaveDailyGoal.class, DeleteDailyGoal.class})
    static class DailyGoals implements Runnable {
        @Spec CommandSpec spec;

        @Override public void run() { spec.commandLine().usage(System.out); }
    }

    @Command(name = "list", aliases = "ls", description = "List every configured daily goal.")
    static class ListDailyGoals extends CliCommand {
        public Integer call() { return root().withClient(client -> ok(client.admin().dailyGoals())); }
    }

    @Command(name = "save", aliases = {"add", "set"},
            description = "Add a daily goal, or edit the one already using --code.")
    static class SaveDailyGoal extends CliCommand {
        @Option(names = "--code", description = "Existing goal code. Omit to add a new one.") String code;
        @Option(names = "--name", description = "Shown to players. Required when adding.") String name;
        @Option(names = "--metric", description = "GAMES, WINS, LOSSES, or DRAWS. Required when adding.") String metric;
        @Option(names = "--target", description = "How many are needed that day. Required when adding.") Long target;
        @Option(names = {"--reward", "--reward-points"}, description = "Points paid once per day.") Long reward;
        @Option(names = "--enabled", negatable = true, description = "Hide it from players without deleting it.") Boolean enabled;
        @Option(names = "--reason", required = true) String reason;

        public Integer call() {
            return root().withClient(client -> {
                // The API replaces the whole list, so read it, change one row, and send it back.
                var goals = new ArrayList<>(client.admin().dailyGoals());
                var index = indexOf(goals, code);
                if (code != null && index < 0) throw new IllegalArgumentException("No daily goal with code: " + code);
                var existing = index < 0 ? null : goals.get(index);
                if (existing == null && (name == null || metric == null || target == null))
                    throw new IllegalArgumentException("--name, --metric, and --target are required when adding");
                var updated = new AdminDailyGoalDTO(
                        existing == null ? null : existing.code(),
                        name != null ? name : existing.name(),
                        metric != null ? metric.toUpperCase() : existing.metric(),
                        target != null ? target : existing.target(),
                        reward != null ? reward : existing == null ? 0L : existing.rewardPoints(),
                        enabled != null ? enabled : existing == null || existing.enabled());
                if (updated.target() != null && updated.target() <= 0)
                    throw new IllegalArgumentException("--target must be above 0");
                if (updated.rewardPoints() != null && updated.rewardPoints() < 0)
                    throw new IllegalArgumentException("--reward cannot be negative");
                if (!root().confirmChange(existing == null ? "new daily goal" : "daily goal " + code,
                        existing == null ? "not configured" : summary(existing), summary(updated))) return 5;
                if (index < 0) goals.add(updated); else goals.set(index, updated);
                return ok(client.admin().updateDailyGoals(new AdminDailyGoalsPatchDTO(goals, reason)));
            });
        }

        private static int indexOf(List<AdminDailyGoalDTO> goals, String code) {
            if (code == null) return -1;
            for (int index = 0; index < goals.size(); index++)
                if (code.equals(goals.get(index).code())) return index;
            return -1;
        }

        private static String summary(AdminDailyGoalDTO goal) {
            return "%s: %s %d for %dP%s".formatted(goal.name(), goal.metric(), goal.target(),
                    goal.rewardPoints() == null ? 0 : goal.rewardPoints(),
                    goal.enabled() != null && !goal.enabled() ? " (disabled)" : "");
        }
    }

    @Command(name = "delete", aliases = {"rm", "remove"}, description = "Remove a daily goal.")
    static class DeleteDailyGoal extends CliCommand {
        @Parameters(index = "0", paramLabel = "CODE") String code;
        @Option(names = "--reason", required = true) String reason;

        public Integer call() {
            return root().withClient(client -> {
                var remaining = new ArrayList<AdminDailyGoalDTO>();
                var found = false;
                for (var goal : client.admin().dailyGoals()) {
                    if (code.equals(goal.code())) found = true; else remaining.add(goal);
                }
                if (!found) throw new IllegalArgumentException("No daily goal with code: " + code);
                if (!root().confirm("Delete daily goal " + code + "? Players keep what they already earned today."))
                    return 5;
                return ok(client.admin().updateDailyGoals(new AdminDailyGoalsPatchDTO(remaining, reason)));
            });
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
        @Option(names = "--achievement", description = "Repeatable NAME|REWARD|GAMES|WINS|LOSSES|DRAWS|GAME_TYPES|GAME_MODES|HIDDEN|DESCRIPTION goal.") List<String> achievementSpecs = new ArrayList<>();
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
            if (fields.length < 8 || fields.length > 10 || fields[0].isBlank())
                throw new IllegalArgumentException("Achievement must be NAME|REWARD|GAMES|WINS|LOSSES|DRAWS|GAME_TYPES|GAME_MODES|HIDDEN|DESCRIPTION");
            var withModes = fields.length == 10;
            var gameTypes = fields[6].isBlank() ? List.<String>of()
                    : List.of(fields[6].trim().toUpperCase().split(","));
            var gameModes = withModes && !fields[7].isBlank()
                    ? List.of(fields[7].trim().toUpperCase().split(",")) : List.<String>of();
            var hiddenIndex = withModes ? 8 : 7;
            if (!fields[hiddenIndex].equalsIgnoreCase("true") && !fields[hiddenIndex].equalsIgnoreCase("false"))
                throw new IllegalArgumentException("hidden must be true or false");
            var description = withModes ? fields[9].trim() : fields.length == 9 ? fields[8].trim() : "";
            return new AdminPointEventPatchDTO.Achievement(null, fields[0].trim(), description,
                    number(fields[2], "games"), number(fields[3], "wins"), number(fields[4], "losses"),
                    number(fields[5], "draws"), points(fields[1], "reward"), gameTypes, gameModes,
                    Boolean.parseBoolean(fields[hiddenIndex]));
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
