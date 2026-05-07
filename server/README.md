# `server` module

## Overview
- It provides the backend and a web-ui for the card games.
- The backend is built with Spring Boot and uses a Postgres database to store the game state and user information.
- The module has to be connected to a database aka the db has to be launched.

## Set up

- Create a postgres database called `ultracards` and user for the server.
- Development defaults live in `src/main/resources/application-dev.properties`.
- Production settings live in `src/main/resources/application-prod.properties`.
- Shared settings live in `src/main/resources/application.properties`.
- Add the database _user_ and _user password_ to the profile file you are using.
- Set `MAIL_USERNAME` and `MAIL_PASSWORD` in your shell or IDE run configuration instead of 
putting mail credentials in the properties.

Example:
```bash
export MAIL_USERNAME="your-address@gmail.com"
export MAIL_PASSWORD="your-app-password"
```

## Profiles

- The server defaults to the `dev` profile via `spring.profiles.default=dev`.
- Run production config with `--spring.profiles.active=prod` or `SPRING_PROFILES_ACTIVE=prod`.

## Database setup

- The recommended database is Postgres, but any database supported by Spring Boot should work.
- The development database is expected to be running on `localhost` and listening on port `5432`.
- The credentials, port and database type can be changed in the relevant profile file under `src/main/resources/`.
- A Dockerfile to spin up the Postgres instance with the expected database/user/password lives at `docker/postgres/Dockerfile`.
- Build the image: `docker build -t ultracards-db -f docker/postgres/Dockerfile docker/postgres`.
- If the migrations break, you can fix it by running:
```bash
mvn flyway:repair \
  -Dflyway.url=jdbc:postgresql://localhost:5432/ultracards \
  -Dflyway.user=ultracards_user \
  -Dflyway.password=ultracards123 \
  -Dflyway.locations=filesystem:src/main/resources/db/migration
```