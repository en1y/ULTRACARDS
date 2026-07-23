package com.ultracards.cli;

import com.ultracards.gateway.dto.auth.ProfileDTO;
import com.ultracards.gateway.dto.admin.AdminLobbyDTO;
import com.ultracards.gateway.dto.admin.AdminPageDTO;
import com.ultracards.gateway.dto.admin.AdminUserSummaryDTO;
import com.ultracards.gateway.dto.games.GamePlayerDTO;
import com.ultracards.gateway.dto.games.GameTypeDTO;
import com.ultracards.gateway.dto.games.games.briskula.BriskulaGameConfigDTO;
import com.ultracards.gateway.dto.games.lobby.GameLobbyDTO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.jline.reader.Candidate;
import org.jline.reader.Parser;
import org.jline.reader.impl.DefaultParser;
import org.jline.terminal.Size;
import org.jline.terminal.impl.DumbTerminal;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Set;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
    void everyCommandSupportsLocalHelp() {
        var output = new StringWriter();
        var command = new UltracardsAdminCli(new ConfigStore(directory)).commandLine();
        command.setOut(new PrintWriter(output));

        assertEquals(0, command.execute("user", "--help"));
        assertTrue(output.toString().contains("user ["), output.toString());
        assertTrue(output.toString().contains("Show one user by ID, email, or username."), output.toString());
    }

    @Test
    void helpCommandShowsTargetedGameExamples() {
        var output = new StringWriter();
        var command = new UltracardsAdminCli(new ConfigStore(directory)).commandLine();
        command.setOut(new PrintWriter(output));

        assertEquals(0, command.execute("game", "disable", "--help"));

        assertTrue(output.toString().contains("Disable a game or one of its modes"), output.toString());
        assertTrue(output.toString().contains("--mode"), output.toString());
    }

    @Test
    void leaderboardCommandDisplaysAReadableRankedPage() {
        var store = new ConfigStore(directory);
        store.add("local", "http://localhost:8080");
        var template = new org.springframework.web.client.RestTemplate();
        var server = MockRestServiceServer.bindTo(template).build();
        server.expect(request -> {
                    assertEquals("http://localhost:8080/api/leaderboards?metric=WIN_RATE&page=0&size=25&gameType=Briskula&mode=TWO_PLAYERS",
                            request.getURI().toString());
                    assertEquals(HttpMethod.GET, request.getMethod());
                })
                .andRespond(withSuccess("""
                        {"items":[{"position":1,"userId":7,"username":"ada","gamesPlayed":42,
                          "wins":30,"winRate":71.428,"currentUser":true}],"page":0,"size":25,
                         "totalElements":1,"totalPages":1,"currentUserPosition":1,"minimumGames":10,
                         "metric":"WIN_RATE","gameType":"Briskula","mode":"TWO_PLAYERS",
                         "availableModes":["TWO_PLAYERS"]}
                        """, MediaType.APPLICATION_JSON));
        var root = new UltracardsAdminCli(store) {
            @Override org.springframework.web.client.RestTemplate restTemplateWithImmediateTokenPersistence() {
                return template;
            }
        };
        var bytes = new ByteArrayOutputStream();
        var original = System.out;
        try {
            System.setOut(new java.io.PrintStream(bytes));
            assertEquals(0, root.commandLine().execute("leaderboard", "--metric", "WIN_RATE",
                    "--game", "Briskula", "--mode", "TWO_PLAYERS", "--no-color"));
        } finally {
            System.setOut(original);
        }
        server.verify();

        var table = bytes.toString(StandardCharsets.UTF_8);
        assertTrue(table.contains("Rank"), table);
        assertTrue(table.contains("ada (you)"), table);
        assertTrue(table.contains("71.4%"), table);
        assertTrue(table.contains("Page 1 of 1 · 1 ranked · WIN_RATE · Briskula / TWO_PLAYERS"), table);
        assertTrue(table.contains("Minimum 10 games"), table);
        assertTrue(table.contains("Your position: #1"), table);
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
    void oneShotProfileSelectionDoesNotChangeTheDefault() {
        var store = new ConfigStore(directory);
        store.add("local", "http://localhost:8080");
        store.add("production", "https://cards.example.com");
        var output = new ByteArrayOutputStream();
        var original = System.out;
        try {
            System.setOut(new java.io.PrintStream(output));
            assertEquals(0, new UltracardsAdminCli(store).commandLine()
                    .execute("server", "current", "--profile", "production"));
        } finally {
            System.setOut(original);
        }

        assertEquals("local", store.activeProfile());
        assertTrue(output.toString(StandardCharsets.UTF_8).contains("https://cards.example.com"));
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
    void interactiveShellExitsCleanly() throws Exception {
        var input = new ByteArrayInputStream("exit\n".getBytes(StandardCharsets.UTF_8));
        var output = new ByteArrayOutputStream();
        try (var terminal = new DumbTerminal(input, output)) {
            var root = new UltracardsAdminCli(new ConfigStore(directory));
            root.noColor = true;
            root.store.add("production", "https://cards.example.com");
            root.store.token("session-token");
            terminal.setSize(new Size(120, 40));

            assertEquals(0, Shell.run(root, terminal));
            var welcome = output.toString(StandardCharsets.UTF_8);
            assertTrue(welcome.contains("____"));
            assertTrue(welcome.contains("SESSION"));
            assertTrue(welcome.contains("production"));
            assertTrue(welcome.contains("system status"));
            assertTrue(welcome.contains("ultracards[production]"));
        }
    }

    @Test
    void interactiveReaderHandlesNavigationCompletionAndHistory() throws Exception {
        var keys = "elp\033[Hh\033[Fx\033[D\033[3~\nserv\tlist\n\033[A\n";
        var input = new ByteArrayInputStream(keys.getBytes(StandardCharsets.UTF_8));
        try (var terminal = new DumbTerminal(input, new ByteArrayOutputStream())) {
            var reader = Shell.reader(new UltracardsAdminCli(new ConfigStore(directory)), terminal);

            assertEquals("help", reader.readLine());
            assertEquals("server list", reader.readLine());
            assertEquals("server list", reader.readLine());
        }
    }

    @Test
    void shellCommandInsideTheDefaultShellDoesNotOpenAnotherTerminal() throws Exception {
        var input = new ByteArrayInputStream("shell\nexit\n".getBytes(StandardCharsets.UTF_8));
        var output = new ByteArrayOutputStream();
        var original = System.out;
        try (var terminal = new DumbTerminal(input, new ByteArrayOutputStream())) {
            System.setOut(new java.io.PrintStream(output));
            assertEquals(0, Shell.run(new UltracardsAdminCli(new ConfigStore(directory)), terminal));
        } finally {
            System.setOut(original);
        }
        assertTrue(output.toString(StandardCharsets.UTF_8).contains("Already in the interactive shell"));
    }

    @Test
    void clearCommandRedrawsTheStartupScreen() throws Exception {
        var input = new ByteArrayInputStream("clear\nexit\n".getBytes(StandardCharsets.UTF_8));
        var output = new ByteArrayOutputStream();
        try (var terminal = new DumbTerminal(input, output)) {
            var root = new UltracardsAdminCli(new ConfigStore(directory));
            root.noColor = true;

            assertEquals(0, Shell.run(root, terminal));
        }

        var screen = output.toString(StandardCharsets.UTF_8);
        assertEquals(2, screen.split("ULTRACARDS ADMIN v", -1).length - 1);
    }

    @Test
    void interactiveCompletionRanksCommandsBeforeOptions() {
        var root = new UltracardsAdminCli(new ConfigStore(directory));
        var line = new DefaultParser().parse("", 0, Parser.ParseContext.COMPLETE);
        var candidates = new java.util.ArrayList<Candidate>();

        Shell.completer(root.commandLine().getCommandSpec()).complete(null, line, candidates);
        candidates.sort(null);

        var firstOption = candidates.stream().map(Candidate::value).toList().indexOf("--allow-insecure");
        var lastCommand = candidates.stream().map(Candidate::value).toList().indexOf("whoami");
        assertTrue(lastCommand >= 0);
        assertTrue(firstOption > lastCommand);
        assertTrue(candidates.stream().anyMatch(candidate -> candidate.value().equals("clear")));
        assertEquals("Commands", candidates.stream().filter(candidate -> candidate.value().equals("server"))
                .findFirst().orElseThrow().group());
        assertEquals("Options", candidates.stream().filter(candidate -> candidate.value().equals("--help"))
                .findFirst().orElseThrow().group());
    }

    @Test
    void welcomeScreenFitsNarrowTerminals() throws Exception {
        var output = new ByteArrayOutputStream();
        try (var terminal = new DumbTerminal(new ByteArrayInputStream(new byte[0]), output)) {
            var root = new UltracardsAdminCli(new ConfigStore(directory));
            root.noColor = true;
            terminal.setSize(new Size(48, 24));

            WelcomeScreen.print(root, terminal, picocli.CommandLine.Help.Ansi.OFF);

            var welcome = output.toString(StandardCharsets.UTF_8);
            assertTrue(welcome.contains("Remote server administration console"));
            assertFalse(welcome.contains("____"));
            for (var line : welcome.lines().toList()) assertTrue(line.length() <= 48, line);
        }
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

    @Test
    void humanTablesRenderGatewayDtoPropertiesInsteadOfObjectIdentity() {
        var profile = new ProfileDTO();
        profile.setId(7L);
        profile.setEmail("admin@example.com");
        profile.setUsername("admin");
        profile.setRoles(List.of("USER", "ADMIN"));
        var original = System.out;
        var bytes = new java.io.ByteArrayOutputStream();
        try {
            System.setOut(new java.io.PrintStream(bytes));
            new OutputWriter().print(profile, OutputWriter.Format.TABLE, false, false, false);
        } finally {
            System.setOut(original);
        }

        var table = bytes.toString();
        assertTrue(table.contains("admin@example.com"));
        assertTrue(table.contains("admin"));
        assertTrue(table.contains("[\"USER\",\"ADMIN\"]"));
        assertFalse(table.contains("ProfileDTO@"));
    }

    @Test
    void lobbyTablesShowAdministrativeSummaryColumns() {
        var id = UUID.fromString("f92e3047-d5e5-4eef-9d0d-8b13d35e8ec3");
        var owner = new GamePlayerDTO("en1y", 7L);
        var config = new BriskulaGameConfigDTO(4, 3, true, List.of(owner));
        var lobby = new GameLobbyDTO(id, "ULTRALobby", 2, 4, Set.of(owner), owner, GameTypeDTO.Briskula,
                false, "CARDS7", false, config, Instant.parse("2026-07-17T18:18:26Z"));
        var original = System.out;
        var bytes = new ByteArrayOutputStream();
        try {
            System.setOut(new java.io.PrintStream(bytes));
            new OutputWriter().print(List.of(new AdminLobbyDTO(lobby, "PUBLIC", Instant.parse("2026-07-17T18:18:26Z"))),
                    OutputWriter.Format.TABLE, false, false, false);
        } finally {
            System.setOut(original);
        }

        var table = bytes.toString();
        assertTrue(table.contains("Id"));
        assertTrue(table.contains(id.toString()));
        assertTrue(table.contains("Code"));
        assertTrue(table.contains("CARDS7"));
        assertTrue(table.contains("Owner"));
        assertTrue(table.contains("en1y (#7)"));
        assertTrue(table.contains("1 / 4"));
        assertTrue(table.contains("FOUR_PLAYERS_WITH_TEAMS"));
        assertTrue(table.contains("Briskula"));
        assertFalse(table.contains("{\"id\""));
    }

    @Test
    void lobbyCloseResolvesUuidOrOwnerId() {
        var lobbyId = UUID.fromString("f92e3047-d5e5-4eef-9d0d-8b13d35e8ec3");
        var lobby = new GameLobbyDTO();
        lobby.setId(lobbyId);
        lobby.setHost(new GamePlayerDTO("owner", 42L));
        var queried = new boolean[1];

        assertEquals(lobbyId, LobbyCommands.resolveLobbyId(lobbyId.toString(), () -> {
            queried[0] = true;
            return List.of();
        }));
        assertFalse(queried[0]);
        assertEquals(lobbyId, LobbyCommands.resolveLobbyId("42",
                () -> List.of(new AdminLobbyDTO(lobby, "PUBLIC", Instant.now()))));
        assertEquals(lobbyId, LobbyCommands.resolveLobbyId("owner",
                () -> List.of(new AdminLobbyDTO(lobby, "PUBLIC", Instant.now()))));
    }

    @Test
    void lobbyTargetsResolveCodesAndRejectAmbiguousNames() {
        var first = new GameLobbyDTO();
        first.setId(UUID.randomUUID());
        first.setLobbyCode("CARDS7");
        first.setName("Friday game");
        var second = new GameLobbyDTO();
        second.setId(UUID.randomUUID());
        second.setLobbyCode("CARDS8");
        second.setName("Friday game");
        var lobbies = List.of(new AdminLobbyDTO(first, "PUBLIC", Instant.now()),
                new AdminLobbyDTO(second, "PUBLIC", Instant.now()));

        assertEquals(first.getId(), LobbyCommands.resolveLobbyId("cards7", () -> lobbies));
        assertThrows(UltracardsAdminCli.CliStateException.class,
                () -> LobbyCommands.resolveLobbyId("Friday game", () -> lobbies));
    }

    @Test
    void userTargetsResolveIdsEmailsAndUsernamesAcrossPages() {
        var first = new AdminUserSummaryDTO(7L, "first@example.com", "first", true, false, "ACTIVE",
                Set.of("USER"), null, null, null);
        var second = new AdminUserSummaryDTO(9L, "second@example.com", "second", true, false, "ACTIVE",
                Set.of("USER"), null, null, null);
        var requested = new ArrayList<Integer>();

        assertEquals(7L, UserCommands.resolveUserId("7", page -> {
            throw new AssertionError("Numeric IDs must not perform a lookup");
        }));
        assertEquals(9L, UserCommands.resolveUserId("SECOND@EXAMPLE.COM", page -> {
            requested.add(page);
            return page == 0 ? new AdminPageDTO<>(List.of(first), 0, 1, 2, 2)
                    : new AdminPageDTO<>(List.of(second), 1, 1, 2, 2);
        }));
        assertEquals(List.of(0, 1), requested);
    }

    @Test
    @SuppressWarnings("unchecked")
    void allPagesFetchesUntilTheServerReportedLastPage() {
        var command = new CliCommand() {
            public Integer call() { return 0; }
        };
        var requested = new ArrayList<Integer>();

        var result = (List<String>) command.pages(0, 1, true, (page, size) -> {
            requested.add(page);
            return new AdminPageDTO<>(List.of(page == 0 ? "first" : "second"), page, size, 2, 2);
        });

        assertEquals(List.of("first", "second"), result);
        assertEquals(List.of(0, 1), requested);
    }

    @Test
    void userEditConfirmationShowsResolvedValues() {
        var store = new ConfigStore(directory);
        store.add("local", "http://localhost:8080");
        store.token("session-token");
        var template = new org.springframework.web.client.RestTemplate();
        var server = MockRestServiceServer.bindTo(template).build();
        var current = """
                {"id":1,"email":"old@example.com","username":"old","enabled":true,"status":"ACTIVE",
                 "roles":["USER"],"createdAt":null,"updatedAt":null,"lastLoginAt":null}
                """;
        var proposed = current.replace("\"username\":\"old\"", "\"username\":\"bozo\"");
        var endpoint = "http://localhost:8080/api/admin/v1/users/1";
        server.expect(request -> {
                    assertEquals(endpoint, request.getURI().toString());
                    assertEquals(HttpMethod.GET, request.getMethod());
                })
                .andRespond(withSuccess(current, MediaType.APPLICATION_JSON));
        server.expect(request -> {
                    assertEquals(endpoint, request.getURI().toString());
                    assertEquals(HttpMethod.PATCH, request.getMethod());
                    var body = ((MockClientHttpRequest) request).getBodyAsString();
                    assertTrue(body.contains("\"username\":\"bozo\""), body);
                    assertTrue(body.contains("\"dryRun\":true"), body);
                })
                .andRespond(withSuccess(proposed, MediaType.APPLICATION_JSON));
        server.expect(request -> {
                    assertEquals(endpoint, request.getURI().toString());
                    assertEquals(HttpMethod.PATCH, request.getMethod());
                    var body = ((MockClientHttpRequest) request).getBodyAsString();
                    assertTrue(body.contains("\"username\":\"bozo\""), body);
                    assertTrue(body.contains("\"dryRun\":false"), body);
                })
                .andRespond(withSuccess(proposed, MediaType.APPLICATION_JSON));
        var root = new UltracardsAdminCli(store) {
            @Override org.springframework.web.client.RestTemplate restTemplateWithImmediateTokenPersistence() {
                return template;
            }
        };
        var output = new ByteArrayOutputStream();
        var original = System.err;
        try {
            System.setErr(new java.io.PrintStream(output));
            assertEquals(0, root.commandLine().execute("db", "edit", "user", "1", "--username", "bozo",
                    "--reason", "because", "--yes"));
        } finally {
            System.setErr(original);
        }
        server.verify();

        var review = output.toString(StandardCharsets.UTF_8);
        assertTrue(review.contains("Proposed  AdminUserSummaryDTO[id=1, email=old@example.com, username=bozo, "
                + "enabled=true, fakeAdmin=false, status=ACTIVE"), review);
        assertFalse(review.contains("Proposed  AdminUserPatchDTO"), review);
    }

    private record ExampleRow(String displayName, boolean active) {}
}
