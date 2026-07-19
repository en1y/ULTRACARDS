# Repository Guidelines

## Project Structure & Module Organization

ULTRACARDS is a Java 25 multi-module Maven project. The root `pom.xml` aggregates `server`, `game-gateway`, `game-logic`, and `ui`.

- `server/` contains the Spring Boot backend, Thymeleaf pages, Flyway migrations in `src/main/resources/db/migration`, and profile properties.
- `game-gateway/` contains DTOs and client services used to call the server APIs.
- `game-logic/` contains shared card templates, card implementations, game modules, and the game recorder.
- `ui/` contains console, GUI, web UI placeholders, and `card-to-png` image conversion assets under `src/main/resources/images`.
- Use the standard Maven layout: production Java in `src/main/java`, resources in `src/main/resources`, and tests in `src/test/java`.

## Build, Test, and Development Commands

- `mvn clean install` builds every module and installs local artifacts for cross-module dependencies.
- `mvn test` runs all Maven tests.
- `mvn -pl server -am test` runs tests for `server` plus required modules.
- `mvn -pl server -am spring-boot:run` starts the backend with the default `dev` profile.
- `docker build -t ultracards-db -f server/docker/postgres/Dockerfile server/docker/postgres` builds the local Postgres image described by the server docs.

## Coding Style & Naming Conventions

Use Java 25, UTF-8, and 4-space indentation. Keep packages under `com.ultracards` for application code and under `en1y.ultracards` for Maven coordinates. Name classes in `PascalCase`, methods and fields in `camelCase`, constants in `UPPER_SNAKE_CASE`, and SQL migrations as `V<N>__short_description.sql`. Prefer `var` for local variables when the inferred type remains clear, and avoid lambdas or stream pipelines in favor of simple loops. Prefer existing Spring, DTO, template, and Lombok patterns before adding new abstractions.

## Date and Time Conventions

Treat Monday as the first day of the week and use 24-hour time formatting throughout the application and user-facing UI.

## Frontend UI Verification

When updating frontend UI, test the resulting UX on both mobile and PC/desktop layouts before submitting the change.

## Testing Guidelines

The server module includes JUnit 5, Mockito, Spring Boot Test, and Spring Security Test. Add tests under the matching module's `src/test/java` tree and name them `*Test.java` for unit tests or `*IT.java` for integration tests. Cover game rules, DTO validation, service behavior, controller security paths, and Flyway-affecting database changes. Run `mvn test` before submitting changes.

## Commit & Pull Request Guidelines

Recent commits use short imperative or past-tense summaries such as `removed orphaned UserMatchupStats.java` and `Add Flyway support...`; keep messages concise and focused on one change. For PRs, include a brief description, affected modules, test results, linked issues, and screenshots when changing Thymeleaf/UI behavior. Call out migrations, profile changes, or new environment variables explicitly.

## Security & Configuration Tips

Do not commit real credentials. Local database defaults live in `server/src/main/resources/application-dev.properties`; production settings belong in `application-prod.properties` or environment variables. Set `MAIL_USERNAME` and `MAIL_PASSWORD` in the shell or IDE run configuration.

## Graphify Usage

If `graphify` is available on `PATH` and `graphify-out/graph.json` exists, agents must use Graphify before reading broad source context for codebase questions. Start with `graphify query "<question>"`, `graphify explain "<symbol>"`, or `graphify path "<A>" "<B>"`, then inspect only the source files surfaced by that scoped result. After code changes, run `graphify update .` when Graphify is available to keep the local graph current.
