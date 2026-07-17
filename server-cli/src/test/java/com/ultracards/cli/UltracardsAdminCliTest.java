package com.ultracards.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.client.MockRestServiceServer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Set;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class UltracardsAdminCliTest {
    @TempDir Path directory;

    @Test
    void storesNamedProfilesAndSelectsTheFirstOne() {
        var store = new ConfigStore(directory);
        var command = new UltracardsAdminCli(store).commandLine();

        assertEquals(0, command.execute("server", "add", "local", "http://localhost:8080/"));

        assertEquals("local", store.activeProfile());
        assertEquals("http://localhost:8080", store.activeUrl());
    }

    @Test
    void changingAProfileUrlDropsTheTokenBoundToTheOldServer() {
        var store = new ConfigStore(directory);
        store.add("production", "https://old.example.com");
        store.token("old-server-token");

        store.add("production", "https://new.example.com");

        assertEquals("https://new.example.com", store.activeUrl());
        assertNull(store.token());
    }

    @Test
    void missingServerUsesTheDocumentedStateExitCode() {
        var command = new UltracardsAdminCli(new ConfigStore(directory)).commandLine();

        assertEquals(5, command.execute("whoami"));
    }

    @Test
    void refusesRemotePlainHttpBeforeConnecting() {
        var store = new ConfigStore(directory);
        store.add("remote", "http://example.invalid");

        var command = new UltracardsAdminCli(store).commandLine();

        assertEquals(2, command.execute("whoami"));
    }

    @Test
    void invalidDurationUsesTheDocumentedInputExitCode() {
        var command = new UltracardsAdminCli(new ConfigStore(directory)).commandLine();

        assertEquals(2, command.execute("lobby", "extend", UUID.randomUUID().toString(),
                "--by", "tomorrow", "--reason", "test"));
    }

    @Test
    void persistsRotatedTokenImmediatelyAfterAResponse() throws Exception {
        var store = new ConfigStore(directory);
        store.add("local", "http://localhost:8080");
        var template = new UltracardsAdminCli(store).restTemplateWithImmediateTokenPersistence();
        var mockServer = MockRestServiceServer.bindTo(template).build();
        var headers = new HttpHeaders();
        headers.add(HttpHeaders.SET_COOKIE, "refreshToken=rotated-token; Path=/; HttpOnly");
        mockServer.expect(request -> assertEquals("http://localhost:8080/rotate", request.getURI().toString()))
                .andRespond(withSuccess().headers(headers).body("{}"));

        template.getForEntity("http://localhost:8080/rotate", String.class);
        mockServer.verify();

        assertEquals("rotated-token", store.token());
        if (Files.getFileStore(directory).supportsFileAttributeView("posix"))
            assertEquals(Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE),
                    Files.getPosixFilePermissions(directory.resolve("credentials.properties")));
    }

    @Test
    void persistsServerSideTokenClearingImmediatelyAfterAResponse() {
        var store = new ConfigStore(directory);
        store.add("local", "http://localhost:8080");
        store.token("expired-token");
        var template = new UltracardsAdminCli(store).restTemplateWithImmediateTokenPersistence();
        var mockServer = MockRestServiceServer.bindTo(template).build();
        var headers = new HttpHeaders();
        headers.add(HttpHeaders.SET_COOKIE, "refreshToken=; Path=/; Max-Age=0; HttpOnly");
        mockServer.expect(request -> assertEquals("http://localhost:8080/expired", request.getURI().toString()))
                .andRespond(withSuccess().headers(headers));

        template.getForEntity("http://localhost:8080/expired", Void.class);
        mockServer.verify();

        assertNull(store.token());
    }

    @Test
    void helpProvidesAFirstRunPathAndDescribesTheCommandGroups() {
        var output = new StringWriter();
        var command = new UltracardsAdminCli(new ConfigStore(directory)).commandLine();
        command.setOut(new PrintWriter(output));

        assertEquals(0, command.execute("--help"));

        var help = output.toString();
        assertTrue(help.contains("ULTRACARDS ADMIN"));
        assertTrue(help.contains("Start here:"));
        assertTrue(help.contains("Inspect accounts, roles, status, and sessions."));
        assertTrue(help.contains("help <command>"));
        assertFalse(help.contains("\u001B["));
    }

    @Test
    void invalidCommandsShowSuggestionsAndAConciseHelpHint() {
        var errors = new StringWriter();
        var command = new UltracardsAdminCli(new ConfigStore(directory)).commandLine();
        command.setErr(new PrintWriter(errors, true));

        assertEquals(2, command.execute("looby"));

        assertTrue(errors.toString().contains("Did you mean:"), errors.toString());
        assertTrue(errors.toString().contains("lobby"), errors.toString());
    }

    @Test
    void globalOptionsWorkAfterSubcommands() {
        var root = new UltracardsAdminCli(new ConfigStore(directory));

        root.commandLine().parseArgs("server", "list", "--output", "json", "--utc", "--no-color");

        assertEquals(OutputWriter.Format.JSON, root.output);
        assertTrue(root.utc);
        assertTrue(root.noColor);
    }

    @Test
    void repeatedShellParsesResetInheritedConfirmationFlags() {
        var root = new UltracardsAdminCli(new ConfigStore(directory));
        var command = root.commandLine();

        command.parseArgs("server", "list", "--yes");
        assertTrue(root.yes);

        command.parseArgs("server", "list");
        assertFalse(root.yes);
    }

    @Test
    void humanTablesUseReadableHeadersAndBorders() {
        var original = System.out;
        var bytes = new java.io.ByteArrayOutputStream();
        try {
            System.setOut(new java.io.PrintStream(bytes));
            new OutputWriter().print(List.of(new ExampleRow("Ada", true)), OutputWriter.Format.TABLE,
                    false, false, false);
        } finally {
            System.setOut(original);
        }

        var table = bytes.toString();
        assertTrue(table.contains("┌"));
        assertTrue(table.contains("Display Name"));
        assertTrue(table.contains("Ada"));
    }

    private record ExampleRow(String displayName, boolean active) {}
}
