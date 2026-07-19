@echo off
setlocal

set "SCRIPT_DIR=%~dp0"

if "%JAR_PATH%"=="" (
    for %%F in ("%SCRIPT_DIR%*.jar") do (
        if exist "%%~fF" if "%JAR_PATH%"=="" set "JAR_PATH=%%~fF"
    )
)

if "%JAR_PATH%"=="" (
    echo No jar found next to start-server.bat 1>&2
    exit /b 1
)

if "%SPRING_PROFILES_ACTIVE%"=="" set "SPRING_PROFILES_ACTIVE=prod"

rem General properties: application.properties
if "%APP_TITLE%"=="" set "APP_TITLE=ULTRACARDS Server"
if "%APP_VERSION%"=="" set "APP_VERSION=0.3.2"
if "%SERVER_PORT%"=="" set "SERVER_PORT=8080"
if "%SPRING_PROFILES_DEFAULT%"=="" set "SPRING_PROFILES_DEFAULT=dev"
if "%SPRING_CONFIG_IMPORT%"=="" set "SPRING_CONFIG_IMPORT=optional:classpath:ultrakill-levels.properties"

if "%JPA_DATABASE%"=="" set "JPA_DATABASE=postgresql"
if "%SQL_INIT_PLATFORM%"=="" set "SQL_INIT_PLATFORM=postgres"
if "%FLYWAY_BASELINE_ON_MIGRATE%"=="" set "FLYWAY_BASELINE_ON_MIGRATE=true"
if "%DB_STARTUP_CHECK_ENABLED%"=="" set "DB_STARTUP_CHECK_ENABLED=true"
if "%DB_STARTUP_CHECK_TIMEOUT_MS%"=="" set "DB_STARTUP_CHECK_TIMEOUT_MS=3000"

if "%MAIL_HOST%"=="" set "MAIL_HOST=smtp.gmail.com"
if "%MAIL_PORT%"=="" set "MAIL_PORT=587"
if "%MAIL_USERNAME%"=="" set "MAIL_USERNAME="
if "%MAIL_PASSWORD%"=="" set "MAIL_PASSWORD="
if "%MAIL_SMTP_AUTH%"=="" set "MAIL_SMTP_AUTH=true"
if "%MAIL_SMTP_STARTTLS_ENABLE%"=="" set "MAIL_SMTP_STARTTLS_ENABLE=true"
if "%MAIL_FROM_NAME%"=="" set "MAIL_FROM_NAME=ULTRACARDS"
if "%MAIL_STARTUP_CHECK_ENABLED%"=="" set "MAIL_STARTUP_CHECK_ENABLED=true"
if "%MAIL_STARTUP_CHECK_TIMEOUT_MS%"=="" set "MAIL_STARTUP_CHECK_TIMEOUT_MS=5000"

if "%TOKEN_DURATION_MINUTES%"=="" set "TOKEN_DURATION_MINUTES=10"
if "%TOKEN_UPDATE_PRIVILEGE_DURATION_MINUTES%"=="" set "TOKEN_UPDATE_PRIVILEGE_DURATION_MINUTES=5"
if "%TOKEN_ROTATED_TOKEN_REUSE_SECONDS%"=="" set "TOKEN_ROTATED_TOKEN_REUSE_SECONDS=30"
if "%COOKIE_TOKEN_DURATION_DAYS%"=="" set "COOKIE_TOKEN_DURATION_DAYS=15"
if "%COOKIE_TOKEN_SAME_SITE%"=="" set "COOKIE_TOKEN_SAME_SITE=Lax"

if "%LOBBY_TIMER_DURATION_SECONDS%"=="" set "LOBBY_TIMER_DURATION_SECONDS=300"
if "%PRESENCE_ONLINE_TIMEOUT_SECONDS%"=="" set "PRESENCE_ONLINE_TIMEOUT_SECONDS=60"

if "%MAX_LENGTH_USERNAME%"=="" set "MAX_LENGTH_USERNAME=30"
if "%MAX_LENGTH_EMAIL%"=="" set "MAX_LENGTH_EMAIL=150"
if "%VERIFICATION_CODE_VALIDITY_MINUTES%"=="" set "VERIFICATION_CODE_VALIDITY_MINUTES=15"

if "%ERROR_INCLUDE_MESSAGE%"=="" set "ERROR_INCLUDE_MESSAGE=never"
if "%ERROR_INCLUDE_BINDING_ERRORS%"=="" set "ERROR_INCLUDE_BINDING_ERRORS=never"
if "%ERROR_INCLUDE_STACKTRACE%"=="" set "ERROR_INCLUDE_STACKTRACE=never"

if /I not "%SPRING_PROFILES_ACTIVE%"=="dev" (
    rem Prod properties: application-prod.properties
    if "%SPRING_DEVTOOLS_ADD_PROPERTIES%"=="" set "SPRING_DEVTOOLS_ADD_PROPERTIES=false"
    if "%DB_URL%"=="" set "DB_URL=jdbc:postgresql://localhost:5432/ultracards"
    if "%DB_USERNAME%"=="" set "DB_USERNAME=ultracards_user"
    if "%DB_PASSWORD%"=="" set "DB_PASSWORD=ultracards123"
    if "%DB_DDL_AUTO%"=="" set "DB_DDL_AUTO=update"
    if "%DB_SHOW_SQL%"=="" set "DB_SHOW_SQL=false"
    if "%LOGGING_LEVEL_ROOT%"=="" set "LOGGING_LEVEL_ROOT=INFO"
    if "%LOGGING_LEVEL_ULTRACARDS%"=="" set "LOGGING_LEVEL_ULTRACARDS=INFO"
    if "%LOGGING_LEVEL_SPRING%"=="" set "LOGGING_LEVEL_SPRING=WARN"
    if "%LOGGING_LEVEL_SPRING_WEB%"=="" set "LOGGING_LEVEL_SPRING_WEB=WARN"
    if "%LOGGING_LEVEL_DISPATCHER_SERVLET%"=="" set "LOGGING_LEVEL_DISPATCHER_SERVLET=OFF"
    if "%LOGGING_LEVEL_FRAMEWORK_SERVLET%"=="" set "LOGGING_LEVEL_FRAMEWORK_SERVLET=OFF"
    if "%LOGGING_LEVEL_CATALINA%"=="" set "LOGGING_LEVEL_CATALINA=ERROR"
    if "%LOGGING_LEVEL_HIKARI%"=="" set "LOGGING_LEVEL_HIKARI=ERROR"
    if "%LOGGING_LEVEL_HIBERNATE_ORM%"=="" set "LOGGING_LEVEL_HIBERNATE_ORM=ERROR"
    if "%LOGGING_LEVEL_HIBERNATE_SQL%"=="" set "LOGGING_LEVEL_HIBERNATE_SQL=WARN"
    if "%LOGGING_LEVEL_HIBERNATE_BIND%"=="" set "LOGGING_LEVEL_HIBERNATE_BIND=ERROR"
    if "%BRISKULA_MOVE_TIMER_DURATION_SECONDS%"=="" set "BRISKULA_MOVE_TIMER_DURATION_SECONDS=30"
    if "%COOKIE_TOKEN_SECURE%"=="" set "COOKIE_TOKEN_SECURE=true"
    if "%COOKIE_TOKEN_DOMAIN%"=="" set "COOKIE_TOKEN_DOMAIN="
)

if /I "%SPRING_PROFILES_ACTIVE%"=="dev" (
    rem Dev properties: application-dev.properties
    if "%SPRING_DEVTOOLS_ADD_PROPERTIES%"=="" set "SPRING_DEVTOOLS_ADD_PROPERTIES=true"
    if "%DB_URL%"=="" set "DB_URL=jdbc:postgresql://localhost:5432/ultracards"
    if "%DB_USERNAME%"=="" set "DB_USERNAME=ultracards_user"
    if "%DB_PASSWORD%"=="" set "DB_PASSWORD=ultracards123"
    if "%DB_DDL_AUTO%"=="" set "DB_DDL_AUTO=update"
    if "%DB_SHOW_SQL%"=="" set "DB_SHOW_SQL=true"
    if "%LOGGING_LEVEL_ROOT%"=="" set "LOGGING_LEVEL_ROOT=INFO"
    if "%LOGGING_LEVEL_ULTRACARDS%"=="" set "LOGGING_LEVEL_ULTRACARDS=DEBUG"
    if "%LOGGING_LEVEL_SPRING%"=="" set "LOGGING_LEVEL_SPRING=INFO"
    if "%LOGGING_LEVEL_SPRING_WEB%"=="" set "LOGGING_LEVEL_SPRING_WEB=INFO"
    if "%LOGGING_LEVEL_DISPATCHER_SERVLET%"=="" set "LOGGING_LEVEL_DISPATCHER_SERVLET=OFF"
    if "%LOGGING_LEVEL_FRAMEWORK_SERVLET%"=="" set "LOGGING_LEVEL_FRAMEWORK_SERVLET=OFF"
    if "%LOGGING_LEVEL_CATALINA%"=="" set "LOGGING_LEVEL_CATALINA=ERROR"
    if "%LOGGING_LEVEL_HIKARI%"=="" set "LOGGING_LEVEL_HIKARI=ERROR"
    if "%LOGGING_LEVEL_HIBERNATE_ORM%"=="" set "LOGGING_LEVEL_HIBERNATE_ORM=ERROR"
    if "%LOGGING_LEVEL_HIBERNATE_SQL%"=="" set "LOGGING_LEVEL_HIBERNATE_SQL=INFO"
    if "%LOGGING_LEVEL_HIBERNATE_BIND%"=="" set "LOGGING_LEVEL_HIBERNATE_BIND=TRACE"
    if "%BRISKULA_MOVE_TIMER_DURATION_SECONDS%"=="" set "BRISKULA_MOVE_TIMER_DURATION_SECONDS=5"
    if "%COOKIE_TOKEN_SECURE%"=="" set "COOKIE_TOKEN_SECURE=false"
    if "%COOKIE_TOKEN_DOMAIN%"=="" set "COOKIE_TOKEN_DOMAIN="
)

java %JAVA_OPTS% -jar "%JAR_PATH%" ^
    "--spring.profiles.active=%SPRING_PROFILES_ACTIVE%" ^
    "--spring.profiles.default=%SPRING_PROFILES_DEFAULT%" ^
    "--spring.config.import=%SPRING_CONFIG_IMPORT%" ^
    "--server.port=%SERVER_PORT%" ^
    "--app.title=%APP_TITLE%" ^
    "--app.version=%APP_VERSION%" ^
    "--spring.jpa.database=%JPA_DATABASE%" ^
    "--spring.sql.init.platform=%SQL_INIT_PLATFORM%" ^
    "--spring.datasource.url=%DB_URL%" ^
    "--spring.datasource.username=%DB_USERNAME%" ^
    "--spring.datasource.password=%DB_PASSWORD%" ^
    "--spring.jpa.hibernate.ddl-auto=%DB_DDL_AUTO%" ^
    "--spring.jpa.show-sql=%DB_SHOW_SQL%" ^
    "--spring.flyway.baseline-on-migrate=%FLYWAY_BASELINE_ON_MIGRATE%" ^
    "--app.database.startup-check.enabled=%DB_STARTUP_CHECK_ENABLED%" ^
    "--app.database.startup-check.timeout-ms=%DB_STARTUP_CHECK_TIMEOUT_MS%" ^
    "--spring.mail.host=%MAIL_HOST%" ^
    "--spring.mail.port=%MAIL_PORT%" ^
    "--spring.mail.username=%MAIL_USERNAME%" ^
    "--spring.mail.password=%MAIL_PASSWORD%" ^
    "--spring.mail.properties.mail.smtp.auth=%MAIL_SMTP_AUTH%" ^
    "--spring.mail.properties.mail.smtp.starttls.enable=%MAIL_SMTP_STARTTLS_ENABLE%" ^
    "--app.mail.from.name=%MAIL_FROM_NAME%" ^
    "--app.mail.startup-check.enabled=%MAIL_STARTUP_CHECK_ENABLED%" ^
    "--app.mail.startup-check.timeout-ms=%MAIL_STARTUP_CHECK_TIMEOUT_MS%" ^
    "--app.token.duration-minutes=%TOKEN_DURATION_MINUTES%" ^
    "--app.token.update-privilege-duration-minutes=%TOKEN_UPDATE_PRIVILEGE_DURATION_MINUTES%" ^
    "--app.token.rotated-token-reuse-seconds=%TOKEN_ROTATED_TOKEN_REUSE_SECONDS%" ^
    "--app.cookie-token.duration-days=%COOKIE_TOKEN_DURATION_DAYS%" ^
    "--app.cookie-token.same-site=%COOKIE_TOKEN_SAME_SITE%" ^
    "--app.cookie-token.secure=%COOKIE_TOKEN_SECURE%" ^
    "--app.cookie-token.domain=%COOKIE_TOKEN_DOMAIN%" ^
    "--app.lobby.timer.duration-seconds=%LOBBY_TIMER_DURATION_SECONDS%" ^
    "--app.briskula-move.timer.duration-seconds=%BRISKULA_MOVE_TIMER_DURATION_SECONDS%" ^
    "--app.presence.online-timeout-seconds=%PRESENCE_ONLINE_TIMEOUT_SECONDS%" ^
    "--app.max-length.username=%MAX_LENGTH_USERNAME%" ^
    "--app.max-length.email=%MAX_LENGTH_EMAIL%" ^
    "--app.verification-code.validity-minutes=%VERIFICATION_CODE_VALIDITY_MINUTES%" ^
    "--spring.devtools.add-properties=%SPRING_DEVTOOLS_ADD_PROPERTIES%" ^
    "--logging.level.root=%LOGGING_LEVEL_ROOT%" ^
    "--logging.level.com.ultracards=%LOGGING_LEVEL_ULTRACARDS%" ^
    "--logging.level.org.springframework=%LOGGING_LEVEL_SPRING%" ^
    "--logging.level.org.springframework.web=%LOGGING_LEVEL_SPRING_WEB%" ^
    "--logging.level.org.springframework.web.servlet.DispatcherServlet=%LOGGING_LEVEL_DISPATCHER_SERVLET%" ^
    "--logging.level.org.springframework.web.servlet.FrameworkServlet=%LOGGING_LEVEL_FRAMEWORK_SERVLET%" ^
    "--logging.level.org.apache.catalina=%LOGGING_LEVEL_CATALINA%" ^
    "--logging.level.com.zaxxer.hikari=%LOGGING_LEVEL_HIKARI%" ^
    "--logging.level.org.hibernate.orm=%LOGGING_LEVEL_HIBERNATE_ORM%" ^
    "--logging.level.org.hibernate.SQL=%LOGGING_LEVEL_HIBERNATE_SQL%" ^
    "--logging.level.org.hibernate.orm.jdbc.bind=%LOGGING_LEVEL_HIBERNATE_BIND%" ^
    "--spring.web.error.include-message=%ERROR_INCLUDE_MESSAGE%" ^
    "--spring.web.error.include-binding-errors=%ERROR_INCLUDE_BINDING_ERRORS%" ^
    "--spring.web.error.include-stacktrace=%ERROR_INCLUDE_STACKTRACE%"
