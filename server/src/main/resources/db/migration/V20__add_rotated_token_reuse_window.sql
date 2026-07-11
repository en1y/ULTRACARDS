ALTER TABLE tokens
    ADD COLUMN rotated_to_token_id UUID,
    ADD COLUMN reuse_until TIMESTAMP WITH TIME ZONE;

ALTER TABLE tokens
    ADD CONSTRAINT fk_tokens_rotated_to_token
        FOREIGN KEY (rotated_to_token_id) REFERENCES tokens (id)
        ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_tokens_rotated_to_token_id
    ON tokens (rotated_to_token_id);

CREATE INDEX IF NOT EXISTS idx_tokens_reuse_until
    ON tokens (reuse_until);
