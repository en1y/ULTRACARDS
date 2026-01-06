package com.ultracards.gateway.dto.updated.games;

import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.KeyDeserializer;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GamePlayerKeyDeserializer extends KeyDeserializer {
    private static final Pattern NAME_PATTERN = Pattern.compile("name=([^,\\)]*)", Pattern.CASE_INSENSITIVE);
    private static final Pattern ID_PATTERN = Pattern.compile("id=([^,\\)]*)", Pattern.CASE_INSENSITIVE);
    private static final Pattern DIGIT_PATTERN = Pattern.compile("(\\d+)");

    @Override
    public Object deserializeKey(String key, DeserializationContext ctxt) throws IOException {
        if (key == null) return null;
        String trimmed = key.trim();
        if (trimmed.isEmpty()) return null;

        String name = null;
        Long id = null;

        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            try {
                ObjectMapper mapper = new ObjectMapper();
                var node = mapper.readTree(trimmed);
                if (node.has("name")) {
                    name = node.get("name").asText();
                }
                if (node.has("id")) {
                    id = node.get("id").asLong();
                }
            } catch (IOException ignored) {
                // Fall through to regex parsing.
            }
        }

        if (name == null) {
            Matcher matcher = NAME_PATTERN.matcher(trimmed);
            if (matcher.find()) {
                name = matcher.group(1).trim();
            }
        }

        if (id == null) {
            Matcher matcher = ID_PATTERN.matcher(trimmed);
            if (matcher.find()) {
                id = parseLong(matcher.group(1));
            }
        }

        if (id == null) {
            Matcher matcher = DIGIT_PATTERN.matcher(trimmed);
            if (matcher.find()) {
                id = parseLong(matcher.group(1));
            }
        }

        if (id == null) {
            id = 0L;
        }
        if (name == null || name.isBlank()) {
            name = "Player " + id;
        }

        return new GamePlayerDTO(name, id);
    }

    private Long parseLong(String value) {
        if (value == null) return null;
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
