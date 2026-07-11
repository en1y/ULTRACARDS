CREATE TABLE IF NOT EXISTS tokens (
    id UUID NOT NULL,
    token VARCHAR(255) NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    active BOOLEAN NOT NULL,
    user_id BIGINT NOT NULL,
    CONSTRAINT pk_tokens PRIMARY KEY (id),
    CONSTRAINT uk_tokens_token UNIQUE (token),
    CONSTRAINT fk_tokens_user
        FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE INDEX IF NOT EXISTS idx_tokens_user_id
    ON tokens (user_id);

CREATE TABLE IF NOT EXISTS sessions (
    id UUID NOT NULL,
    user_id BIGINT,
    token_id UUID NOT NULL,
    device_id VARCHAR(255),
    client_type VARCHAR(255),
    os VARCHAR(255),
    ip_hash VARCHAR(255) NOT NULL,
    country VARCHAR(255),
    region VARCHAR(255),
    user_agent VARCHAR(512),
    first_seen_at TIMESTAMP WITH TIME ZONE NOT NULL,
    last_seen_at TIMESTAMP WITH TIME ZONE NOT NULL,
    last_authenticated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_sessions PRIMARY KEY (id),
    CONSTRAINT uk_sessions_token UNIQUE (token_id),
    CONSTRAINT fk_sessions_token
        FOREIGN KEY (token_id) REFERENCES tokens (id)
);

CREATE INDEX IF NOT EXISTS idx_sessions_user_id
    ON sessions (user_id);

CREATE TABLE IF NOT EXISTS user_game_stats_entries (
    user_game_stats_id UUID NOT NULL,
    game_type VARCHAR(255) NOT NULL,
    played INTEGER NOT NULL,
    wins INTEGER NOT NULL,
    CONSTRAINT pk_user_game_stats_entries PRIMARY KEY (user_game_stats_id, game_type),
    CONSTRAINT fk_user_game_stats_entries_user_game_stats
        FOREIGN KEY (user_game_stats_id) REFERENCES user_game_stats (id)
        ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_user_game_stats_entries_user_game_stats_id
    ON user_game_stats_entries (user_game_stats_id);

ALTER TABLE sessions
    DROP CONSTRAINT IF EXISTS fk_sessions_token;

ALTER TABLE sessions
    ADD CONSTRAINT fk_sessions_token
        FOREIGN KEY (token_id) REFERENCES tokens (id)
        ON DELETE CASCADE;

ALTER TABLE user_game_stats_entries
    DROP CONSTRAINT IF EXISTS fk_user_game_stats_entries_user_game_stats;

ALTER TABLE user_game_stats_entries
    ADD CONSTRAINT fk_user_game_stats_entries_user_game_stats
        FOREIGN KEY (user_game_stats_id) REFERENCES user_game_stats (id)
        ON DELETE CASCADE;
