-- Bet fee overrides. mode '*' covers every mode of the game; a named mode wins over it,
-- and point_settings.wager_fee_percent stays the fallback when neither row exists.
CREATE TABLE point_wager_fees (
    game_type VARCHAR(32) NOT NULL,
    mode VARCHAR(128) NOT NULL,
    fee_percent SMALLINT NOT NULL,
    CONSTRAINT pk_point_wager_fees PRIMARY KEY (game_type, mode),
    CONSTRAINT chk_point_wager_fees_percent CHECK (fee_percent BETWEEN 0 AND 100)
);
