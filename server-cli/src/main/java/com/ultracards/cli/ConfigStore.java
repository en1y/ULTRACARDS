package com.ultracards.cli;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

final class ConfigStore {
    private final Path directory;
    private final Path configFile;
    private final Path credentialsFile;
    private final Properties config = new Properties();
    private final Properties credentials = new Properties();

    ConfigStore() {
        this(resolveDirectory());
    }

    ConfigStore(Path directory) {
        this.directory = directory;
        this.configFile = directory.resolve("config.properties");
        this.credentialsFile = directory.resolve("credentials.properties");
        load(configFile, config);
        load(credentialsFile, credentials);
    }

    Map<String, String> profiles() {
        var output = new LinkedHashMap<String, String>();
        for (var key : config.stringPropertyNames())
            if (key.startsWith("server.")) output.put(key.substring(7), config.getProperty(key));
        return output;
    }

    String activeProfile() { return config.getProperty("active"); }

    String activeUrl() {
        var active = activeProfile();
        return active == null ? null : config.getProperty("server." + active);
    }

    void add(String name, String url) {
        validateName(name);
        var key = "server." + name;
        var normalizedUrl = stripSlash(url);
        var previousUrl = config.getProperty(key);
        config.setProperty(key, normalizedUrl);
        if (previousUrl != null && !previousUrl.equals(normalizedUrl)) {
            credentials.remove("token." + name);
            saveCredentials();
        }
        if (activeProfile() == null) config.setProperty("active", name);
        saveConfig();
    }

    void use(String name) {
        if (!profiles().containsKey(name)) throw new IllegalArgumentException("Unknown server profile: " + name);
        config.setProperty("active", name);
        saveConfig();
    }

    void remove(String name) {
        config.remove("server." + name);
        credentials.remove("token." + name);
        if (name.equals(activeProfile())) config.remove("active");
        saveConfig();
        saveCredentials();
    }

    String token() {
        var active = activeProfile();
        return active == null ? null : credentials.getProperty("token." + active);
    }

    Path historyFile() {
        try { Files.createDirectories(directory); }
        catch (IOException ex) { throw new IllegalStateException("Cannot create CLI config directory", ex); }
        return directory.resolve("history");
    }

    void token(String token) {
        var active = activeProfile();
        if (active == null) throw new IllegalStateException("No active server profile");
        if (token == null || token.isBlank()) credentials.remove("token." + active);
        else credentials.setProperty("token." + active, token);
        saveCredentials();
    }

    private void saveConfig() { save(configFile, config, false); }
    private void saveCredentials() { save(credentialsFile, credentials, true); }

    private void load(Path path, Properties values) {
        if (!Files.exists(path)) return;
        try (Reader reader = Files.newBufferedReader(path)) { values.load(reader); }
        catch (IOException ex) { throw new IllegalStateException("Cannot read " + path + ": " + ex.getMessage(), ex); }
    }

    private void save(Path path, Properties values, boolean secret) {
        try {
            Files.createDirectories(directory);
            var temporary = Files.createTempFile(directory, path.getFileName().toString(), ".tmp");
            try (Writer writer = Files.newBufferedWriter(temporary)) { values.store(writer, "ULTRACARDS admin CLI"); }
            if (secret) secure(temporary);
            Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            if (secret) secure(path);
        } catch (IOException ex) {
            throw new IllegalStateException("Cannot write " + path + ": " + ex.getMessage(), ex);
        }
    }

    private void secure(Path path) {
        try { Files.setPosixFilePermissions(path, Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE)); }
        catch (UnsupportedOperationException | IOException ignored) { /* Windows or a non-POSIX filesystem. */ }
    }

    private void validateName(String name) {
        if (name == null || !name.matches("[A-Za-z0-9._-]+"))
            throw new IllegalArgumentException("Profile names may contain letters, digits, dot, underscore, and dash");
    }

    private String stripSlash(String url) {
        var value = url.trim();
        while (value.endsWith("/")) value = value.substring(0, value.length() - 1);
        return value;
    }

    private static Path resolveDirectory() {
        var appData = System.getenv("APPDATA");
        if (System.getProperty("os.name", "").toLowerCase().contains("windows") && appData != null)
            return Path.of(appData, "ULTRACARDS", "admin-cli");
        var xdg = System.getenv("XDG_CONFIG_HOME");
        var base = xdg == null || xdg.isBlank() ? Path.of(System.getProperty("user.home"), ".config") : Path.of(xdg);
        return base.resolve("ultracards").resolve("admin-cli");
    }
}
