package com.ultracards.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.i18n.CookieLocaleResolver;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.concurrent.ConcurrentHashMap;

@Configuration
public class I18nConfig {

    public static final String LANGUAGE_COOKIE = "uc-lang";
    public static final List<String> SUPPORTED_LANGUAGES = List.of("en", "hr", "uk", "de");

    private static final Map<String, Map<String, String>> MESSAGES_CACHE = new ConcurrentHashMap<>();
    private final boolean reloadMessages;

    public I18nConfig(@Value("${app.i18n.reload-messages:false}") boolean reloadMessages) {
        this.reloadMessages = reloadMessages;
    }

    /**
     * No default locale: without the cookie the resolver falls back to the
     * request's Accept-Language header. The language selector JS writes the
     * cookie directly and reloads, so no LocaleChangeInterceptor is needed.
     */
    @Bean
    public LocaleResolver localeResolver() {
        var resolver = new CookieLocaleResolver(LANGUAGE_COOKIE);
        resolver.setCookieMaxAge(Duration.ofDays(365));
        return resolver;
    }

    /** Clamps any locale to a supported language code, falling back to English. */
    public static String supportedLanguage(Locale locale) {
        var language = locale != null ? locale.getLanguage() : "";
        return SUPPORTED_LANGUAGES.contains(language) ? language : "en";
    }

    /** All UI messages for the locale, used to fill window.__I18N__ for the JS side. */
    public Map<String, String> messagesFor(Locale locale) {
        var language = supportedLanguage(locale);
        if (reloadMessages) {
            ResourceBundle.clearCache(I18nConfig.class.getClassLoader());
            return loadMessages(language);
        }
        var cached = MESSAGES_CACHE.get(language);
        if (cached == null) {
            cached = loadMessages(language);
            MESSAGES_CACHE.put(language, cached);
        }
        return cached;
    }

    private Map<String, String> loadMessages(String language) {
        // No-fallback control: an unmatched language must land on the base
        // (English) bundle, never on the JVM default locale's bundle.
        var bundle = ResourceBundle.getBundle("i18n.messages", Locale.of(language),
                ResourceBundle.Control.getNoFallbackControl(ResourceBundle.Control.FORMAT_PROPERTIES));
        var messages = new LinkedHashMap<String, String>();
        for (var key : bundle.keySet()) {
            messages.put(key, bundle.getString(key));
        }
        return Map.copyOf(messages);
    }
}
