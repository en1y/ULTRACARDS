package com.ultracards.webui.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 * Configuration class for RestTemplate.
 * Provides a bean for making HTTP requests to the server module.
 */
@Configuration
public class RestTemplateConfig {

    @Value("${app.ultracards.server.port}")
    private String serverPort;

    /**
     * Creates a RestTemplate bean for making HTTP requests.
     *
     * @return The configured RestTemplate
     */
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

    /**
     * Returns the base URL for the server module.
     *
     * @return The server base URL
     */
    @Bean
    public String serverBaseUrl() {
        return "http://localhost:" + serverPort;
    }
}