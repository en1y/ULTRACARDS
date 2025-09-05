package com.ultracards.webui.config;

import com.ultracards.gateway.config.ServerIssueResolver;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ServerIssueConfig {

    private final ConfigurableApplicationContext context;

    public ServerIssueConfig(ConfigurableApplicationContext context) {
        this.context = context;
    }

    @Bean
    @Qualifier("serverIssue")
    public ServerIssueResolver serverIssue() {
        return this::exit;
    }

    @Bean
    @Qualifier("serverConnectionIssue")
    public ServerIssueResolver serverConnectionIssue() {
        return this::exit;
    }

    private void exit() {
        var exitCode = SpringApplication.exit(context, () -> 1);
        System.exit(exitCode);
    }
}
