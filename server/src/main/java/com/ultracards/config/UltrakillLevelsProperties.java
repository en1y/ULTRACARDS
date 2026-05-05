package com.ultracards.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

@Data
@Configuration
@ConfigurationProperties(prefix = "ultrakill.levels")
public class UltrakillLevelsProperties {
    private Map<String, String> prelude = new HashMap<>();
    private Map<String, String> act1 = new HashMap<>();
    private Map<String, String> act2 = new HashMap<>();
    private Map<String, String> act3 = new HashMap<>();
    private Map<String, String> finale = new HashMap<>();
    private Map<String, String> primeSanctums = new HashMap<>();
    private Map<String, String> secretLevels = new HashMap<>();
    private Map<String, String> encores = new HashMap<>();
    private Map<String, String> layers = new HashMap<>();
    private Map<String, String> ordinals = new HashMap<>();
}
