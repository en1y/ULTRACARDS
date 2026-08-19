# Repository Guidelines

## Project Structure & Module Organization

ULTRACARDS is a Java 25 multi-module Maven project. The root `pom.xml` aggregates `server`, `game-gateway`, `game-logic`, `ui`, and `server-cli`.

- `server/` contains the Spring Boot backend, Thymeleaf pages, Flyway migrations in `src/main/resources/db/migration`, and profile properties.
- `game-gateway/` contains DTOs and client services used to call the server APIs.
- `game-logic/` contains shared card templates, card implementations, game modules, and the game recorder.
- `ui/` contains console, GUI, web UI placeholders, and `card-to-png` image conversion assets under `src/main/resources/images`.
- Use the standard Maven layout: production Java in `src/main/java`, resources in `src/main/resources`, and tests in `src/test/java`.

## Build, Test, and Development Commands

- `mvn clean install` builds every module and installs local artifacts for cross-module dependencies.
- `mvn test` runs all Maven tests.
- `mvn -pl server -am test` runs tests for `server` plus required modules.
- `docker build -t ultracards-db -f server/docker/postgres/Dockerfile server/docker/postgres` builds the local Postgres image described by the server docs.

### Fast feedback

Prefer these over a full `clean install` when checking a change:

- `mvn -o -pl server -am compile` — compile check only. Use `-o` (offline) throughout; dependency resolution is the slow part.
- `mvn -o -pl server test -Dtest=ClassName` — run a single test class. Add `#methodName` for one method.
- `mvn -o -pl game-logic -am test` — game rule changes rarely need the `server` suite.

Starting the backend takes two steps; `mvn -pl server -am spring-boot:run` does **not** work, because the plugin prefix does not resolve from the aggregator and `-am` runs the goal on the parent ("Unable to find a suitable main class"):

```
mvn -o install -DskipTests
mvn -o -f server/pom.xml spring-boot:run -Dspring-boot.run.jvmArguments=-Dapp.mail.startup-check.enabled=false
```

The mail flag is required unless `MAIL_USERNAME`/`MAIL_PASSWORD` are set, or boot aborts on the mail startup check.

## Coding Style & Naming Conventions

Use Java 25, UTF-8, and 4-space indentation. Keep packages under `com.ultracards` for application code and under `en1y.ultracards` for Maven coordinates. Name classes in `PascalCase`, methods and fields in `camelCase`, constants in `UPPER_SNAKE_CASE`, and SQL migrations as `V<N>__short_description.sql`. Prefer `var` for local variables when the inferred type remains clear, and avoid lambdas or stream pipelines in favor of simple loops. Prefer existing Spring, DTO, template, and Lombok patterns before adding new abstractions.

## Date and Time Conventions

Treat Monday as the first day of the week and use 24-hour time formatting throughout the application and user-facing UI.
When working with dates use dd.mm.yyyy format for UI

## Frontend UI Verification

When updating frontend UI, test the resulting UX on both mobile and PC/desktop layouts before submitting the change.

With the dev profile running, templates and static resources under `server/src/main/resources` are served **straight from source** — editing the file is enough, no rebuild and no copy into `target/classes` (copying there is a no-op that silently misleads). Java changes still need a devtools restart via `mvn -o -pl server -am compile`.

- `/admin/sandbox?type=treseta|briskula` drives the whole live-game UI with fake players and no backend game, via `window.UltracardsSandbox`. Use it for game-UI state testing instead of playing a real match.
- Authenticated pages need a login cookie. Tokens live in PLAINTEXT in the `tokens` table; mint one plus a matching `sessions` row, then set `refreshToken` as a cookie. The cookie is HttpOnly, so one browser can only ever be one player — drive other players over REST with separate cookie jars.
- Headless screenshots hang on authenticated pages (the header's STOMP websocket never lets virtual time expire). Use `--dump-dom` to verify markup; for pixels, drop a temporary harness page into `static/` that stubs `fetch` and loads the real CSS/JS, then delete it.

## Testing Guidelines

The server module includes JUnit 5, Mockito, Spring Boot Test, and Spring Security Test. Add tests under the matching module's `src/test/java` tree and name them `*Test.java` for unit tests or `*IT.java` for integration tests. Cover game rules, DTO validation, service behavior, controller security paths, and Flyway-affecting database changes. Run `mvn test` before submitting changes.

### Scope and known flakiness

- `*PersistenceTest` classes are `@SpringBootTest` against the **shared dev database**, not an isolated one. They are the slow tests and the fragile ones.
- Because they read live settings rows, some fail on a clean checkout for reasons unrelated to your change — e.g. `PointsServicePersistenceTest` hardcodes a 4% wager fee while `point_settings.wager_fee_percent` may hold another value locally. Confirm with `git stash` before chasing a failure you did not cause.
- Manually driven or recorded games leak into assertions like `DurakRecordingPersistenceTest`. Clean up recordings in FK order after generating them.
- Pure game-rule changes only need `mvn -o -pl game-logic -am test`; run the full suite before opening a PR, not on every edit.

## Commit & Pull Request Guidelines

Recent commits use short imperative or past-tense summaries such as `removed orphaned UserMatchupStats.java` and `Add Flyway support...`; keep messages concise and focused on one change. For PRs, include a brief description, affected modules, test results, linked issues, and screenshots when changing Thymeleaf/UI behavior. Call out migrations, profile changes, or new environment variables explicitly.

## Security & Configuration Tips

Do not commit real credentials. Local database defaults live in `server/src/main/resources/application-dev.properties`; production settings belong in `application-prod.properties` or environment variables. Set `MAIL_USERNAME` and `MAIL_PASSWORD` in the shell or IDE run configuration.

## Search & Context Efficiency

This is a large repo; unbounded search output is the main source of wasted context.

- Delegate broad or exploratory searches to a subagent rather than grepping in the main thread. "Which files handle X", "where is Y used", and similar sweeps should be answered by a subagent that returns the conclusion, not the raw matches.
- Narrow before reading. Use `graphify query "<question>" --budget <N>` or `grep -l` to get a candidate file list, then read those files with line offsets. Avoid reading whole files when a range will do — several classes here exceed 1000 lines.
- Prefer `grep -l` / `grep -c` when the file list or a count is the actual answer, and scope the path prefix to one module instead of the repo root.

## graphify

Use the installed Graphify skill when the user invokes Graphify. Otherwise use the graph as a lead for Java, JavaScript, and SQL relationships, then verify against current source.

- Prefer `graphify explain "<specific symbol>"` or `graphify path "<A>" "<B>"`. Natural-language `query` and `affected` are unreliable here because common labels and same-named symbols collide.
- Templates, Markdown, and SQL are in the graph from the free AST pass — no semantic extraction needed. `.html` coverage is fragment-level, not element-level. CSS and `.properties` files are not detected at all, so find styles and i18n keys by path.
- Compare `graphify-out/graph.json`'s `built_at_commit` and the working tree before trusting results.
- Run `graphify update .` after code changes when available; it refreshes structural data only and costs no API tokens.

