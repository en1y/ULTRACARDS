package com.ultracards.service.startup;

import lombok.extern.slf4j.Slf4j;

import java.util.regex.Pattern;

@Slf4j
final class StartupCheckSupport {

    private static final Pattern UNRESOLVED_PLACEHOLDER = Pattern.compile("^\\$\\{.+}$");

    private StartupCheckSupport() {}

    static boolean isUnresolved(String value) {
        return value != null && UNRESOLVED_PLACEHOLDER.matcher(value).matches();
    }

    static String trimToNull(String value) {
        if (value == null) return null;

        var trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    static String summarizeError(Exception ex) {
        var message = ex.getMessage();
        if (message == null || message.isBlank()) {
            return ex.getClass().getSimpleName();
        }

        return message.replace('\n', ' ').replace('\r', ' ').trim();
    }

    static void abort(String message) {
        var fullMessage = "ULTRACARDS Server startup aborted: " + message;

        try { log.error(fullMessage); }
        catch (RuntimeException ignored) {System.err.println(fullMessage);}

        System.exit(1);
    }
}
