package com.ultracards.ui.webui.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class RestTemplateConfig {
    @Value("${app.ultracards.server.url}")
    private String serverUrl;

    @Bean
    @Qualifier("serverUrl")
    public String serverUrl() {
        return serverUrl.endsWith("/") ?
                serverUrl : serverUrl + "/";
    }

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
