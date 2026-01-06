FROM postgres:16-alpine

# Defaults matching the Spring application.properties configuration
ENV POSTGRES_DB=ultracards \
    POSTGRES_USER=ultracards_user \
    POSTGRES_PASSWORD=ultracards123

EXPOSE 5432

# Ensure container reports healthy once Postgres is ready to accept connections
HEALTHCHECK --interval=10s --timeout=5s --retries=5 CMD pg_isready -U "$POSTGRES_USER" -d "$POSTGRES_DB"
