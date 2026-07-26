-- Durak game history. Additive only: existing Briskula and Treseta records are untouched.

CREATE TABLE IF NOT EXISTS recorded_durak_games
(
    id                   UUID PRIMARY KEY REFERENCES recorded_games (id) ON DELETE CASCADE,
    mode_key             VARCHAR(64) NOT NULL,
    number_of_players    INTEGER     NOT NULL,
    -- The physical pack choice (24, 36 or 54) and the Joker toggle are separate columns so the
    -- 54-card pack with and without Jokers stay distinguishable modes.
    deck_size            INTEGER     NOT NULL,
    jokers_enabled       BOOLEAN     NOT NULL,
    throw_in_policy      VARCHAR(32) NOT NULL,
    passing_enabled      BOOLEAN     NOT NULL,
    trump_suit           VARCHAR(16),
    trump_indicator_code VARCHAR(8),
    -- NULL exactly when draw is TRUE; a NULL loser on a non-draw game is a data-integrity error.
    loser_user_id        BIGINT,
    draw                 BOOLEAN     NOT NULL DEFAULT FALSE
);

CREATE INDEX IF NOT EXISTS idx_recorded_durak_games_mode_key
    ON recorded_durak_games (mode_key);

CREATE INDEX IF NOT EXISTS idx_recorded_durak_games_loser_user_id
    ON recorded_durak_games (loser_user_id);

CREATE TABLE IF NOT EXISTS recorded_durak_finish_order
(
    game_id         UUID    NOT NULL REFERENCES recorded_durak_games (id) ON DELETE CASCADE,
    user_id         BIGINT  NOT NULL,
    finish_position INTEGER NOT NULL,
    PRIMARY KEY (game_id, finish_position)
);

CREATE INDEX IF NOT EXISTS idx_recorded_durak_finish_order_user_id
    ON recorded_durak_finish_order (user_id);

-- A Durak card play carries a role, and a defense points at the attack it covers.
-- Existing Briskula/Treseta rows are backfilled with PLAY / NULL.
ALTER TABLE recorded_plays
    ADD COLUMN IF NOT EXISTS action_type VARCHAR(32) NOT NULL DEFAULT 'PLAY';

ALTER TABLE recorded_plays
    ADD COLUMN IF NOT EXISTS target_play_order INTEGER;

CREATE INDEX IF NOT EXISTS idx_recorded_plays_action_type
    ON recorded_plays (action_type);
