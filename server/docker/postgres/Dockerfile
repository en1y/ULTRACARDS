FROM postgres:16-alpine

# Defaults matching the Spring application.properties configuration
ENV POSTGRES_DB=ultracards \
    POSTGRES_USER=ultracards_user \
    POSTGRES_PASSWORD=ultracards123

EXPOSE 5432

# Ensure container reports healthy once Postgres is ready to accept connections
HEALTHCHECK --interval=10s --timeout=5s --retries=5 CMD pg_isready -U "$POSTGRES_USER" -d "$POSTGRES_DB"

# Build the image: `docker build -t ultracards-db -f docker/postgres/Dockerfile docker/postgres`.
# Run the container: `docker run --name ultracards-db -p 5432:5432 -d ultracards-db`. Add `-v ultracards-db-data:/var/lib/postgresql/data`.