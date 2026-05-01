package com.ultracards.service.startup;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationPreparedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.Ordered;
import org.springframework.core.env.Environment;

import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

import static com.ultracards.service.startup.StartupCheckSupport.*;

@Slf4j
public final class DatabaseStartupCheckService
        implements ApplicationListener<ApplicationPreparedEvent>, Ordered {

    @Override
    public void onApplicationEvent(ApplicationPreparedEvent event) {
        var env = event.getApplicationContext().getEnvironment();
        if (!env.getProperty("app.database.startup-check.enabled", Boolean.class, true)) {
            return;
        }

        checkDatabase(env);
    }

    private void checkDatabase(Environment env) {
        var url = trimToNull(env.getProperty("spring.datasource.url"));
        if (url == null) {
            abort("Database startup check failed: spring.datasource.url is not configured.");
            return;
        }

        var username = trimToNull(env.getProperty("spring.datasource.username"));
        var password = env.getProperty("spring.datasource.password");

        if (isUnresolved(url)
                || isUnresolved(username)
                || isUnresolved(password)) {
            abort("Database startup check failed: datasource properties are unresolved for URL " + describe(url) + ".");
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

            log.debug("Database startup check succeeded on {}", describe(url));
        } catch (SQLException ex) {
            abort("Database startup check failed for " + describe(url) + ": " + summarizeError(ex));
        }
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    private static String describe(String url) {
        var questionMarkIndex = url.indexOf('?');
        return questionMarkIndex >= 0 ? url.substring(0, questionMarkIndex) : url;
    }
}
