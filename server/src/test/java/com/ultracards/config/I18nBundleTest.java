package com.ultracards.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Properties;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Every locale bundle must define exactly the same keys as the English base
 * bundle, with no blank values — a missing key renders as ??key?? in the UI.
 */
class I18nBundleTest {

    private static final List<String> LOCALE_FILES =
            List.of("messages_hr.properties", "messages_uk.properties", "messages_de.properties");

    @Test
    void allBundlesHaveIdenticalKeySetsAndNoBlankValues() throws IOException {
        var baseProperties = load("messages.properties");
        var baseKeys = new TreeSet<>(baseProperties.stringPropertyNames());
        assertFalse(baseKeys.isEmpty(), "base bundle must not be empty");
        assertNoBlankValues("messages.properties", baseProperties);

        for (var file : LOCALE_FILES) {
            var properties = load(file);
            assertEquals(baseKeys, new TreeSet<>(properties.stringPropertyNames()),
                    file + " must define exactly the same keys as messages.properties");
            assertNoBlankValues(file, properties);
        }
    }

    private void assertNoBlankValues(String file, Properties properties) {
        for (var key : properties.stringPropertyNames()) {
            assertFalse(properties.getProperty(key).isBlank(), file + ": blank value for key " + key);
        }
    }

    private Properties load(String file) throws IOException {
        var properties = new Properties();
        try (var stream = getClass().getResourceAsStream("/i18n/" + file)) {
            properties.load(new InputStreamReader(stream, StandardCharsets.UTF_8));
        }
        return properties;
    }
}
