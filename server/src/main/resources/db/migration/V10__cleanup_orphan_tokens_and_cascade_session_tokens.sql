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
