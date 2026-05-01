package com.ultracards.service.startup;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationPreparedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.Ordered;

import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;
import java.util.regex.Pattern;

@Slf4j
public final class DatabaseStartupCheckService
        implements ApplicationListener<ApplicationPreparedEvent>, Ordered {

    private static final Pattern UNRESOLVED_PLACEHOLDER = Pattern.compile("^\\$\\{.+}$");

    @Override
    public void onApplicationEvent(ApplicationPreparedEvent event) {
        var env = event.getApplicationContext().getEnvironment();
        var enabled = env.getProperty("app.database.startup-check.enabled", Boolean.class, true);
        if (!enabled) {
            return;
        }

        var url = trimToNull(env.getProperty("spring.datasource.url"));
        if (url == null) {
            abort("database startup check failed: spring.datasource.url is not configured.");
            return;
        }

        var username = trimToNull(env.getProperty("spring.datasource.username"));
        var password = env.getProperty("spring.datasource.password");

        if (isUnresolved(url) || isUnresolved(username) || isUnresolved(password)) {
            abort("database startup check failed: datasource properties are unresolved for URL " + describe(url) + ".");
            return;
        }

        var timeoutMs = Math.max(1000, env.getProperty("app.database.startup-check.timeout-ms", Integer.class, 3000));

        try {
            var loginTimeoutSeconds = Math.max(1, (int) Math.ceil(timeoutMs / 1000.0));
            DriverManager.setLoginTimeout(loginTimeoutSeconds);

            var properties = new Properties();
            if (username != null) properties.setProperty("user", username);
            if (password != null) properties.setProperty("password", password);

            var ignored = DriverManager.getConnection(url, properties);
            ignored.close();

            log.debug("database startup check succeeded on {}", describe(url));
        } catch (SQLException ex) {
            abort("database startup check failed for " + describe(url) + ": " + summarizeError(ex));
        }
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    private static boolean isUnresolved(String value) {
        return value != null && UNRESOLVED_PLACEHOLDER.matcher(value).matches();
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        var trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String describe(String url) {
        var questionMarkIndex = url.indexOf('?');
        return questionMarkIndex >= 0 ? url.substring(0, questionMarkIndex) : url;
    }

    private static String summarizeError(SQLException ex) {
        var message = ex.getMessage();
        if (message == null || message.isBlank()) {
            return ex.getClass().getSimpleName();
        }

        message = message.replace('\n', ' ').replace('\r', ' ').trim();
        return message;
    }

    private static void abort(String message) {
        log.error("ULTRACARDS Server startup aborted: " + message);
        log.error("Check if your database is running and the set DB user and its password are correct.");
        System.exit(1);
    }
}
