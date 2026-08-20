package com.ultracards.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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

    /**
     * The ledger renders each row as points.transaction.&lt;type lowercased&gt;, so a new
     * transaction type without its label ships as a literal ??key?? in the activity table.
     */
    @Test
    void everyTransactionTypeHasALabel() throws IOException {
        // Type is package-private to PointsService, so read the constants from source.
        var source = Files.readString(
                Path.of("src/main/java/com/ultracards/server/service/points/PointsService.java"),
                StandardCharsets.UTF_8);
        var enumBody = source.substring(source.indexOf("private enum Type {") + "private enum Type {".length());
        enumBody = enumBody.substring(0, enumBody.indexOf('}'));

        var properties = load("messages.properties");
        var missing = new TreeSet<String>();
        for (var constant : enumBody.split(",")) {
            var name = constant.replaceAll("(?s)//.*?\n", "").trim();
            if (name.isEmpty()) continue;
            var key = "points.transaction." + name.toLowerCase();
            if (!properties.containsKey(key)) missing.add(key);
        }
        assertEquals(new TreeSet<String>(), missing, "transaction types missing a label");
    }

    private void assertNoBlankValues(String file, Properties properties) {
        for (var key : properties.stringPropertyNames()) {
            var value = properties.getProperty(key);
            assertFalse(value.isBlank(), file + ": blank value for key " + key);
            // Markup belongs in the template or the script that renders the string,
            // never in a translated value — translators must not have to keep tags.
            assertFalse(value.matches("(?s).*<[a-zA-Z/].*"), file + ": HTML markup in key " + key);
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
