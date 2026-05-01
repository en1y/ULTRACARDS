package com.ultracards.service.startup;

import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationPreparedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.Ordered;
import org.springframework.core.env.Environment;

import java.util.Properties;

import static com.ultracards.service.startup.StartupCheckSupport.*;

@Slf4j
public final class MailStartupCheckService
        implements ApplicationListener<ApplicationPreparedEvent>, Ordered {

    @Override
    public void onApplicationEvent(ApplicationPreparedEvent event) {
        var env = event.getApplicationContext().getEnvironment();
        if (!env.getProperty("app.mail.startup-check.enabled", Boolean.class, true)) {
            return;
        }

        checkMail(env);
    }

    private void checkMail(Environment env) {
        var host = trimToNull(env.getProperty("spring.mail.host"));
        if (host == null) {
            abort("Mail startup check failed: spring.mail.host is not configured.");
            return;
        }

        var username = trimToNull(env.getProperty("spring.mail.username"));
        var password = env.getProperty("spring.mail.password");

        if (username == null) {
            abort("Mail startup check failed: spring.mail.username is not configured.");
            return;
        }

        if (password == null || password.isBlank()) {
            abort("Mail startup check failed: spring.mail.password is not configured.");
            return;
        }

        if (isUnresolved(host) || isUnresolved(username) || isUnresolved(password)) {
            abort("Mail startup check failed: mail properties are unresolved for host " + host + ".");
            return;
        }

        var port = env.getProperty("spring.mail.port", Integer.class, 587);
        var timeoutMs = Math.max(1000, env.getProperty("app.mail.startup-check.timeout-ms", Integer.class, 5000));

        var properties = new Properties();
        properties.setProperty("mail.transport.protocol", "smtp");
        properties.setProperty("mail.smtp.host", host);
        properties.setProperty("mail.smtp.port", Integer.toString(port));
        properties.setProperty("mail.smtp.auth", env.getProperty("spring.mail.properties.mail.smtp.auth", "true"));
        properties.setProperty("mail.smtp.starttls.enable",
                env.getProperty("spring.mail.properties.mail.smtp.starttls.enable", "true"));
        properties.setProperty("mail.smtp.connectiontimeout", Integer.toString(timeoutMs));
        properties.setProperty("mail.smtp.timeout", Integer.toString(timeoutMs));
        properties.setProperty("mail.smtp.writetimeout", Integer.toString(timeoutMs));

        var session = Session.getInstance(properties);

        try (Transport transport = session.getTransport("smtp")) {
            transport.connect(host, port, username, password);
            log.debug("Mail startup check succeeded on {}:{}", host, port);
        } catch (MessagingException ex) {
            abort("Mail startup check failed for " + host + ":" + port + ": " + summarizeError(ex));
        }
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 1;
    }
}
