CREATE TABLE point_settings (
    id SMALLINT NOT NULL DEFAULT 1,
    wager_fee_percent SMALLINT NOT NULL DEFAULT 4,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT,
    CONSTRAINT pk_point_settings PRIMARY KEY (id),
    CONSTRAINT chk_point_settings_singleton CHECK (id = 1),
    CONSTRAINT chk_point_settings_wager_fee CHECK (wager_fee_percent BETWEEN 0 AND 100),
    CONSTRAINT fk_point_settings_user FOREIGN KEY (updated_by) REFERENCES users (id) ON DELETE SET NULL
);

INSERT INTO point_settings(id, wager_fee_percent) VALUES (1, 4);

ALTER TABLE point_wagers
    ADD COLUMN fee_percent SMALLINT NOT NULL DEFAULT 4,
    ADD CONSTRAINT chk_point_wagers_fee CHECK (fee_percent BETWEEN 0 AND 100);

ALTER TABLE point_game_results
    ADD COLUMN game_type VARCHAR(16),
    ADD COLUMN outcome VARCHAR(8);

UPDATE point_game_results SET outcome = CASE WHEN won THEN 'WIN' ELSE 'LOSS' END;

ALTER TABLE point_game_results
    ALTER COLUMN outcome SET NOT NULL,
    ADD CONSTRAINT chk_point_game_results_outcome CHECK (outcome IN ('WIN', 'LOSS', 'DRAW'));

CREATE INDEX idx_point_game_results_event_progress
    ON point_game_results (user_id, game_type, ended_at DESC, outcome);

CREATE TABLE point_events (
    id UUID NOT NULL,
    name VARCHAR(120) NOT NULL,
    description VARCHAR(1000) NOT NULL DEFAULT '',
    starts_at TIMESTAMP WITH TIME ZONE NOT NULL,
    ends_at TIMESTAMP WITH TIME ZONE,
    game_types VARCHAR(128) NOT NULL DEFAULT '',
    completion_reward_points BIGINT NOT NULL DEFAULT 0,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_point_events PRIMARY KEY (id),
    CONSTRAINT chk_point_events_name CHECK (btrim(name) <> ''),
    CONSTRAINT chk_point_events_dates CHECK (ends_at IS NULL OR ends_at > starts_at),
    CONSTRAINT chk_point_events_reward CHECK (completion_reward_points >= 0)
);

CREATE INDEX idx_point_events_schedule ON point_events (enabled, starts_at, ends_at);

CREATE TABLE point_event_achievements (
    id UUID NOT NULL,
    event_id UUID NOT NULL,
    name VARCHAR(120) NOT NULL,
    description VARCHAR(500) NOT NULL DEFAULT '',
    games_required INTEGER NOT NULL DEFAULT 0,
    wins_required INTEGER NOT NULL DEFAULT 0,
    losses_required INTEGER NOT NULL DEFAULT 0,
    draws_required INTEGER NOT NULL DEFAULT 0,
    reward_points BIGINT NOT NULL DEFAULT 0,
    sort_order INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT pk_point_event_achievements PRIMARY KEY (id),
    CONSTRAINT fk_point_event_achievements_event FOREIGN KEY (event_id) REFERENCES point_events (id) ON DELETE CASCADE,
    CONSTRAINT chk_point_event_achievements_name CHECK (btrim(name) <> ''),
    CONSTRAINT chk_point_event_achievements_target CHECK (
        games_required > 0 OR wins_required > 0 OR losses_required > 0 OR draws_required > 0
    ),
    CONSTRAINT chk_point_event_achievements_counts CHECK (
        games_required >= 0 AND wins_required >= 0 AND losses_required >= 0 AND draws_required >= 0
    ),
    CONSTRAINT chk_point_event_achievements_reward CHECK (reward_points >= 0)
);

CREATE INDEX idx_point_event_achievements_event ON point_event_achievements (event_id, sort_order, id);
