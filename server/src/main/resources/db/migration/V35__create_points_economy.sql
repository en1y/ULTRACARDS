ALTER TABLE users
    ADD COLUMN points_balance BIGINT NOT NULL DEFAULT 1500,
    ADD COLUMN last_points_claim_date DATE;

ALTER TABLE users
    ADD CONSTRAINT chk_users_points_balance_non_negative CHECK (points_balance >= 0);

CREATE TABLE point_transactions (
    id UUID NOT NULL,
    user_id BIGINT NOT NULL,
    amount BIGINT NOT NULL,
    balance_after BIGINT NOT NULL,
    transaction_type VARCHAR(32) NOT NULL,
    reference_id VARCHAR(128) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_point_transactions PRIMARY KEY (id),
    CONSTRAINT fk_point_transactions_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT uk_point_transactions_reference UNIQUE (user_id, transaction_type, reference_id),
    CONSTRAINT chk_point_transactions_amount_non_zero CHECK (amount <> 0),
    CONSTRAINT chk_point_transactions_balance_non_negative CHECK (balance_after >= 0)
);

CREATE INDEX idx_point_transactions_user_created
    ON point_transactions (user_id, created_at DESC);
CREATE INDEX idx_point_transactions_created
    ON point_transactions (created_at DESC);
CREATE INDEX idx_users_points_leaderboard
    ON users (points_balance DESC, lower(username), id)
    WHERE enabled = TRUE AND status = 'ACTIVE';

CREATE TABLE point_wagers (
    game_id UUID NOT NULL,
    lobby_id UUID NOT NULL,
    stake_points BIGINT NOT NULL,
    status VARCHAR(16) NOT NULL,
    rake_points BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    settled_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT pk_point_wagers PRIMARY KEY (game_id),
    CONSTRAINT chk_point_wagers_stake CHECK (stake_points > 0),
    CONSTRAINT chk_point_wagers_rake CHECK (rake_points >= 0),
    CONSTRAINT chk_point_wagers_status CHECK (status IN ('OPEN', 'SETTLED', 'REFUNDED'))
);

CREATE TABLE point_wager_players (
    game_id UUID NOT NULL,
    user_id BIGINT NOT NULL,
    stake_points BIGINT NOT NULL,
    payout_points BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT pk_point_wager_players PRIMARY KEY (game_id, user_id),
    CONSTRAINT fk_point_wager_players_wager FOREIGN KEY (game_id) REFERENCES point_wagers (game_id) ON DELETE CASCADE,
    CONSTRAINT fk_point_wager_players_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT chk_point_wager_players_stake CHECK (stake_points > 0),
    CONSTRAINT chk_point_wager_players_payout CHECK (payout_points >= 0)
);

CREATE INDEX idx_point_wagers_status ON point_wagers (status, created_at);

INSERT INTO point_transactions (id, user_id, amount, balance_after, transaction_type, reference_id, created_at)
SELECT gen_random_uuid(), id, 1500, 1500, 'INITIAL_GRANT', 'INITIAL', CURRENT_TIMESTAMP
FROM users;
