package com.ultracards.server.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for Jackson ObjectMapper.
 */
@Configuration
public class JacksonConfig {

    /**
     * Creates an ObjectMapper bean with custom configuration.
     *
     * @return the configured ObjectMapper
     */
    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule()); // Support for Java 8 date/time types
        return objectMapper;
    }
}