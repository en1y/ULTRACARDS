#!/usr/bin/env sh
set -eu

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)

if [ "${JAR_PATH:-}" = "" ]; then
    set -- "$SCRIPT_DIR"/*.jar
    if [ -f "$1" ]; then
        JAR_PATH=$1
    else
        echo "No jar found next to start-server.sh" >&2
        exit 1
    fi
fi

SPRING_PROFILES_ACTIVE=${SPRING_PROFILES_ACTIVE:-prod}

# General properties: application.properties
APP_TITLE=${APP_TITLE:-ULTRACARDS Server}
APP_VERSION=${APP_VERSION:-0.2.0}
SERVER_PORT=${SERVER_PORT:-8080}
SPRING_PROFILES_DEFAULT=${SPRING_PROFILES_DEFAULT:-dev}
SPRING_CONFIG_IMPORT=${SPRING_CONFIG_IMPORT:-optional:classpath:ultrakill-levels.properties}

JPA_DATABASE=${JPA_DATABASE:-postgresql}
SQL_INIT_PLATFORM=${SQL_INIT_PLATFORM:-postgres}
FLYWAY_BASELINE_ON_MIGRATE=${FLYWAY_BASELINE_ON_MIGRATE:-true}
DB_STARTUP_CHECK_ENABLED=${DB_STARTUP_CHECK_ENABLED:-true}
DB_STARTUP_CHECK_TIMEOUT_MS=${DB_STARTUP_CHECK_TIMEOUT_MS:-3000}

MAIL_HOST=${MAIL_HOST:-smtp.gmail.com}
MAIL_PORT=${MAIL_PORT:-587}
MAIL_USERNAME=${MAIL_USERNAME:-}
MAIL_PASSWORD=${MAIL_PASSWORD:-}
MAIL_SMTP_AUTH=${MAIL_SMTP_AUTH:-true}
MAIL_SMTP_STARTTLS_ENABLE=${MAIL_SMTP_STARTTLS_ENABLE:-true}
MAIL_FROM_NAME=${MAIL_FROM_NAME:-ULTRACARDS}
MAIL_STARTUP_CHECK_ENABLED=${MAIL_STARTUP_CHECK_ENABLED:-true}
MAIL_STARTUP_CHECK_TIMEOUT_MS=${MAIL_STARTUP_CHECK_TIMEOUT_MS:-5000}

TOKEN_DURATION_MINUTES=${TOKEN_DURATION_MINUTES:-10}
TOKEN_UPDATE_PRIVILEGE_DURATION_MINUTES=${TOKEN_UPDATE_PRIVILEGE_DURATION_MINUTES:-5}
TOKEN_ROTATED_TOKEN_REUSE_SECONDS=${TOKEN_ROTATED_TOKEN_REUSE_SECONDS:-30}
COOKIE_TOKEN_DURATION_DAYS=${COOKIE_TOKEN_DURATION_DAYS:-15}
COOKIE_TOKEN_SAME_SITE=${COOKIE_TOKEN_SAME_SITE:-Lax}

LOBBY_TIMER_DURATION_SECONDS=${LOBBY_TIMER_DURATION_SECONDS:-300}
PRESENCE_ONLINE_TIMEOUT_SECONDS=${PRESENCE_ONLINE_TIMEOUT_SECONDS:-60}

MAX_LENGTH_USERNAME=${MAX_LENGTH_USERNAME:-30}
MAX_LENGTH_EMAIL=${MAX_LENGTH_EMAIL:-150}
VERIFICATION_CODE_VALIDITY_MINUTES=${VERIFICATION_CODE_VALIDITY_MINUTES:-15}

ERROR_INCLUDE_MESSAGE=${ERROR_INCLUDE_MESSAGE:-never}
ERROR_INCLUDE_BINDING_ERRORS=${ERROR_INCLUDE_BINDING_ERRORS:-never}
ERROR_INCLUDE_STACKTRACE=${ERROR_INCLUDE_STACKTRACE:-never}

if [ "$SPRING_PROFILES_ACTIVE" != "dev" ]; then
    # Prod properties: application-prod.properties
    SPRING_DEVTOOLS_ADD_PROPERTIES=${SPRING_DEVTOOLS_ADD_PROPERTIES:-false}
    DB_URL=${DB_URL:-jdbc:postgresql://localhost:5432/ultracards}
    DB_USERNAME=${DB_USERNAME:-ultracards_user}
    DB_PASSWORD=${DB_PASSWORD:-ultracards123}
    DB_DDL_AUTO=${DB_DDL_AUTO:-update}
    DB_SHOW_SQL=${DB_SHOW_SQL:-false}
    LOGGING_LEVEL_ROOT=${LOGGING_LEVEL_ROOT:-INFO}
    LOGGING_LEVEL_ULTRACARDS=${LOGGING_LEVEL_ULTRACARDS:-INFO}
    LOGGING_LEVEL_SPRING=${LOGGING_LEVEL_SPRING:-WARN}
    LOGGING_LEVEL_SPRING_WEB=${LOGGING_LEVEL_SPRING_WEB:-WARN}
    LOGGING_LEVEL_DISPATCHER_SERVLET=${LOGGING_LEVEL_DISPATCHER_SERVLET:-OFF}
    LOGGING_LEVEL_FRAMEWORK_SERVLET=${LOGGING_LEVEL_FRAMEWORK_SERVLET:-OFF}
    LOGGING_LEVEL_CATALINA=${LOGGING_LEVEL_CATALINA:-ERROR}
    LOGGING_LEVEL_HIKARI=${LOGGING_LEVEL_HIKARI:-ERROR}
    LOGGING_LEVEL_HIBERNATE_ORM=${LOGGING_LEVEL_HIBERNATE_ORM:-ERROR}
    LOGGING_LEVEL_HIBERNATE_SQL=${LOGGING_LEVEL_HIBERNATE_SQL:-WARN}
    LOGGING_LEVEL_HIBERNATE_BIND=${LOGGING_LEVEL_HIBERNATE_BIND:-ERROR}
    BRISKULA_MOVE_TIMER_DURATION_SECONDS=${BRISKULA_MOVE_TIMER_DURATION_SECONDS:-30}
    COOKIE_TOKEN_SECURE=${COOKIE_TOKEN_SECURE:-true}
    COOKIE_TOKEN_DOMAIN=${COOKIE_TOKEN_DOMAIN:-}
fi

if [ "$SPRING_PROFILES_ACTIVE" = "dev" ]; then
    # Dev properties: application-dev.properties
    SPRING_DEVTOOLS_ADD_PROPERTIES=${SPRING_DEVTOOLS_ADD_PROPERTIES:-true}
    DB_URL=${DB_URL:-jdbc:postgresql://localhost:5432/ultracards}
    DB_USERNAME=${DB_USERNAME:-ultracards_user}
    DB_PASSWORD=${DB_PASSWORD:-ultracards123}
    DB_DDL_AUTO=${DB_DDL_AUTO:-update}
    DB_SHOW_SQL=${DB_SHOW_SQL:-true}
    LOGGING_LEVEL_ROOT=${LOGGING_LEVEL_ROOT:-INFO}
    LOGGING_LEVEL_ULTRACARDS=${LOGGING_LEVEL_ULTRACARDS:-DEBUG}
    LOGGING_LEVEL_SPRING=${LOGGING_LEVEL_SPRING:-INFO}
    LOGGING_LEVEL_SPRING_WEB=${LOGGING_LEVEL_SPRING_WEB:-INFO}
    LOGGING_LEVEL_DISPATCHER_SERVLET=${LOGGING_LEVEL_DISPATCHER_SERVLET:-OFF}
    LOGGING_LEVEL_FRAMEWORK_SERVLET=${LOGGING_LEVEL_FRAMEWORK_SERVLET:-OFF}
    LOGGING_LEVEL_CATALINA=${LOGGING_LEVEL_CATALINA:-ERROR}
    LOGGING_LEVEL_HIKARI=${LOGGING_LEVEL_HIKARI:-ERROR}
    LOGGING_LEVEL_HIBERNATE_ORM=${LOGGING_LEVEL_HIBERNATE_ORM:-ERROR}
    LOGGING_LEVEL_HIBERNATE_SQL=${LOGGING_LEVEL_HIBERNATE_SQL:-INFO}
    LOGGING_LEVEL_HIBERNATE_BIND=${LOGGING_LEVEL_HIBERNATE_BIND:-TRACE}
    BRISKULA_MOVE_TIMER_DURATION_SECONDS=${BRISKULA_MOVE_TIMER_DURATION_SECONDS:-5}
    COOKIE_TOKEN_SECURE=${COOKIE_TOKEN_SECURE:-false}
    COOKIE_TOKEN_DOMAIN=${COOKIE_TOKEN_DOMAIN:-}
fi

export MAIL_USERNAME MAIL_PASSWORD

exec java ${JAVA_OPTS:-} -jar "$JAR_PATH" \
    "--spring.profiles.active=$SPRING_PROFILES_ACTIVE" \
    "--spring.profiles.default=$SPRING_PROFILES_DEFAULT" \
    "--spring.config.import=$SPRING_CONFIG_IMPORT" \
    "--server.port=$SERVER_PORT" \
    "--app.title=$APP_TITLE" \
    "--app.version=$APP_VERSION" \
    "--spring.jpa.database=$JPA_DATABASE" \
    "--spring.sql.init.platform=$SQL_INIT_PLATFORM" \
    "--spring.datasource.url=$DB_URL" \
    "--spring.datasource.username=$DB_USERNAME" \
    "--spring.datasource.password=$DB_PASSWORD" \
    "--spring.jpa.hibernate.ddl-auto=$DB_DDL_AUTO" \
    "--spring.jpa.show-sql=$DB_SHOW_SQL" \
    "--spring.flyway.baseline-on-migrate=$FLYWAY_BASELINE_ON_MIGRATE" \
    "--app.database.startup-check.enabled=$DB_STARTUP_CHECK_ENABLED" \
    "--app.database.startup-check.timeout-ms=$DB_STARTUP_CHECK_TIMEOUT_MS" \
    "--spring.mail.host=$MAIL_HOST" \
    "--spring.mail.port=$MAIL_PORT" \
    "--spring.mail.username=$MAIL_USERNAME" \
    "--spring.mail.password=$MAIL_PASSWORD" \
    "--spring.mail.properties.mail.smtp.auth=$MAIL_SMTP_AUTH" \
    "--spring.mail.properties.mail.smtp.starttls.enable=$MAIL_SMTP_STARTTLS_ENABLE" \
    "--app.mail.from.name=$MAIL_FROM_NAME" \
    "--app.mail.startup-check.enabled=$MAIL_STARTUP_CHECK_ENABLED" \
    "--app.mail.startup-check.timeout-ms=$MAIL_STARTUP_CHECK_TIMEOUT_MS" \
    "--app.token.duration-minutes=$TOKEN_DURATION_MINUTES" \
    "--app.token.update-privilege-duration-minutes=$TOKEN_UPDATE_PRIVILEGE_DURATION_MINUTES" \
    "--app.token.rotated-token-reuse-seconds=$TOKEN_ROTATED_TOKEN_REUSE_SECONDS" \
    "--app.cookie-token.duration-days=$COOKIE_TOKEN_DURATION_DAYS" \
    "--app.cookie-token.same-site=$COOKIE_TOKEN_SAME_SITE" \
    "--app.cookie-token.secure=$COOKIE_TOKEN_SECURE" \
    "--app.cookie-token.domain=$COOKIE_TOKEN_DOMAIN" \
    "--app.lobby.timer.duration-seconds=$LOBBY_TIMER_DURATION_SECONDS" \
    "--app.briskula-move.timer.duration-seconds=$BRISKULA_MOVE_TIMER_DURATION_SECONDS" \
    "--app.presence.online-timeout-seconds=$PRESENCE_ONLINE_TIMEOUT_SECONDS" \
    "--app.max-length.username=$MAX_LENGTH_USERNAME" \
    "--app.max-length.email=$MAX_LENGTH_EMAIL" \
    "--app.verification-code.validity-minutes=$VERIFICATION_CODE_VALIDITY_MINUTES" \
    "--spring.devtools.add-properties=$SPRING_DEVTOOLS_ADD_PROPERTIES" \
    "--logging.level.root=$LOGGING_LEVEL_ROOT" \
    "--logging.level.com.ultracards=$LOGGING_LEVEL_ULTRACARDS" \
    "--logging.level.org.springframework=$LOGGING_LEVEL_SPRING" \
    "--logging.level.org.springframework.web=$LOGGING_LEVEL_SPRING_WEB" \
    "--logging.level.org.springframework.web.servlet.DispatcherServlet=$LOGGING_LEVEL_DISPATCHER_SERVLET" \
    "--logging.level.org.springframework.web.servlet.FrameworkServlet=$LOGGING_LEVEL_FRAMEWORK_SERVLET" \
    "--logging.level.org.apache.catalina=$LOGGING_LEVEL_CATALINA" \
    "--logging.level.com.zaxxer.hikari=$LOGGING_LEVEL_HIKARI" \
    "--logging.level.org.hibernate.orm=$LOGGING_LEVEL_HIBERNATE_ORM" \
    "--logging.level.org.hibernate.SQL=$LOGGING_LEVEL_HIBERNATE_SQL" \
    "--logging.level.org.hibernate.orm.jdbc.bind=$LOGGING_LEVEL_HIBERNATE_BIND" \
    "--spring.web.error.include-message=$ERROR_INCLUDE_MESSAGE" \
    "--spring.web.error.include-binding-errors=$ERROR_INCLUDE_BINDING_ERRORS" \
    "--spring.web.error.include-stacktrace=$ERROR_INCLUDE_STACKTRACE"
