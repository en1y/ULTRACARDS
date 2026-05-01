package com.ultracards;

import com.ultracards.service.startup.DatabaseStartupCheckService;
import com.ultracards.service.startup.MailStartupCheckService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(
        scanBasePackages = {"com.ultracards.config", "com.ultracards.server", "com.ultracards.ui", "com.ultracards.filters", "com.ultracards.gateway.dto"},
        exclude = org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration.class
)
@EnableScheduling
public class UltracardsServer {

    private static final Logger log = LoggerFactory.getLogger(UltracardsServer.class);

    static void main(String[] args) {
        var application = new SpringApplication(UltracardsServer.class);
        application.addListeners(new DatabaseStartupCheckService());
        application.addListeners(new MailStartupCheckService());
        application.run(args);
    }

    @Bean
    public CommandLineRunner startupMessage(Environment env) {
        return _ -> {
            var port = env.getProperty("server.port", "8080");
            var host = env.getProperty("server.address", "localhost");
            var sslEnabled = env.getProperty("server.ssl.enabled", Boolean.class, false);
            var protocol = sslEnabled ? "https" : "http";

            log.info("╔══════════════════════════════════════════════════════════╗");
            log.info("║ ULTRACARDS server has awakened from its digital slumber. ║");
            log.info("╠══════════════════════════════════════════════════════════╣");
            log.info("║  Local server humming gently at: {}://{}:{}   ║", protocol, host, port);
            log.info("╚══════════════════════════════════════════════════════════╝");

        };
    }
}
