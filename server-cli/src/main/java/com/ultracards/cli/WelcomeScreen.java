package com.ultracards.cli;

import org.jline.terminal.Terminal;
import picocli.CommandLine.Help.Ansi;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

final class WelcomeScreen {
    private static final List<String> LOGO = loadLogo();

    private WelcomeScreen() {}

    static void print(UltracardsAdminCli root, Terminal terminal, Ansi ansi) {
        var reportedWidth = terminal.getWidth();
        var width = Math.max(36, Math.min(reportedWidth > 0 ? reportedWidth - 2 : 100, 112));
        var profile = root.store.activeProfile();
        var target = root.store.activeUrl();
        var authenticated = profile != null && root.store.token() != null;
        var writer = terminal.writer();

        writer.println(red(ansi, titledBorder('╭', "ULTRACARDS ADMIN v" + UltracardsAdminCli.VERSION, '╮', width)));
        if (width >= 84 && !LOGO.isEmpty()) {
            line(writer, ansi, width, "");
            for (var logoLine : LOGO) line(writer, ansi, width, center(logoLine, width - 4), true);
            line(writer, ansi, width, "");
            line(writer, ansi, width, center("REMOTE SERVER ADMINISTRATION", width - 4), true);
        } else {
            line(writer, ansi, width, "Remote server administration console", true);
        }

        writer.println(red(ansi, titledBorder('├', "SESSION", '┤', width)));
        line(writer, ansi, width, "Profile  " + (profile == null ? "not configured" : profile));
        line(writer, ansi, width, "Target   " + (target == null ? "add one with: server add <name> <url>" : target));
        line(writer, ansi, width, "Auth     " + (authenticated ? "saved administrator session" : "not signed in"));

        writer.println(red(ansi, titledBorder('├', "QUICK START", '┤', width)));
        if (profile == null) {
            command(writer, ansi, width, "server add <name> <url>", "connect to a remote server");
            command(writer, ansi, width, "login --email <address>", "authenticate as an administrator");
        } else if (!authenticated) {
            command(writer, ansi, width, "login --email <address>", "authenticate as an administrator");
            command(writer, ansi, width, "server list", "review configured targets");
        } else {
            command(writer, ansi, width, "system status", "check server and database health");
            command(writer, ansi, width, "whoami", "show the active administrator");
        }
        command(writer, ansi, width, "help", "explore every command");
        line(writer, ansi, width, "");
        if (width >= 84) {
            line(writer, ansi, width, "Tab complete  ·  Ctrl-W delete word  ·  Ctrl-D exit", true);
            line(writer, ansi, width, "Ctrl-R search  ·  ↑/↓ history  ·  Ctrl-L clear  ", true);
        } else {
            line(writer, ansi, width, "Tab complete  ·  ↑/↓ history  ·  Ctrl-D exit", true);
        }
        writer.println(red(ansi, "╰" + "─".repeat(width - 2) + "╯"));
        writer.println();
        writer.flush();
    }

    private static void command(java.io.PrintWriter writer, Ansi ansi, int width, String command, String description) {
        var available = width - 4;
        var commandWidth = Math.min(28, Math.max(16, available / 3));
        var text = "› " + pad(command, commandWidth) + "  " + description;
        line(writer, ansi, width, text);
    }

    private static void line(java.io.PrintWriter writer, Ansi ansi, int width, String value) {
        line(writer, ansi, width, value, false);
    }

    private static void line(java.io.PrintWriter writer, Ansi ansi, int width, String value, boolean accent) {
        var contentWidth = width - 4;
        var fitted = fit(value, contentWidth);
        writer.print(red(ansi, "│"));
        writer.print(" ");
        writer.print(accent ? red(ansi, fitted) : fitted);
        writer.print(" ".repeat(contentWidth - fitted.length() + 1));
        writer.println(red(ansi, "│"));
    }

    private static String titledBorder(char left, String title, char right, int width) {
        var label = "─ " + title + " ";
        return left + label + "─".repeat(Math.max(0, width - label.length() - 2)) + right;
    }

    private static String fit(String value, int width) {
        if (value.length() <= width) return value;
        return width < 2 ? value.substring(0, width) : value.substring(0, width - 1) + "…";
    }

    private static String pad(String value, int width) {
        var fitted = fit(value, width);
        return fitted + " ".repeat(width - fitted.length());
    }

    private static String center(String value, int width) {
        var fitted = fit(value, width);
        return " ".repeat(Math.max(0, (width - fitted.length()) / 2)) + fitted;
    }

    private static String red(Ansi ansi, String value) {
        return ansi.string("@|bold,red " + value + "|@");
    }

    private static List<String> loadLogo() {
        var stream = WelcomeScreen.class.getResourceAsStream("/banner.txt");
        if (stream == null) return List.of();
        try (var reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            return reader.lines().takeWhile(line -> !line.isBlank()).toList();
        } catch (Exception ignored) {
            return List.of();
        }
    }
}
