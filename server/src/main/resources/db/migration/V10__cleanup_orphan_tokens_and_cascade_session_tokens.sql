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

DELETE FROM tokens t
WHERE NOT EXISTS (
    SELECT 1
    FROM sessions s
    WHERE s.token_id = t.id
);

ALTER TABLE sessions
    DROP CONSTRAINT IF EXISTS fk_sessions_token;

ALTER TABLE sessions
    ADD CONSTRAINT fk_sessions_token
        FOREIGN KEY (token_id) REFERENCES tokens (id)
        ON DELETE CASCADE;
