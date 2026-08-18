ALTER TABLE point_settings
    ADD COLUMN starting_balance BIGINT NOT NULL DEFAULT 1500,
    ADD COLUMN daily_reward BIGINT NOT NULL DEFAULT 1500,
    ADD CONSTRAINT chk_point_settings_starting_balance CHECK (starting_balance >= 0),
    ADD CONSTRAINT chk_point_settings_daily_reward CHECK (daily_reward > 0);

CREATE TABLE point_daily_goals (
    code VARCHAR(64) NOT NULL,
    name VARCHAR(120) NOT NULL,
    metric VARCHAR(8) NOT NULL,
    target BIGINT NOT NULL,
    reward_points BIGINT NOT NULL DEFAULT 0,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    sort_order INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT pk_point_daily_goals PRIMARY KEY (code),
    CONSTRAINT chk_point_daily_goals_name CHECK (btrim(name) <> ''),
    CONSTRAINT chk_point_daily_goals_metric CHECK (metric IN ('GAMES', 'WINS', 'LOSSES', 'DRAWS')),
    CONSTRAINT chk_point_daily_goals_target CHECK (target > 0),
    CONSTRAINT chk_point_daily_goals_reward CHECK (reward_points >= 0)
);

-- The codes match the enum they replace: today's grants are keyed by "<code>:<date>",
-- so keeping them stops the seeded goals paying out a second time on upgrade day.
INSERT INTO point_daily_goals(code, name, metric, target, reward_points, sort_order) VALUES
    ('FIRST_GAME', 'Play a game', 'GAMES', 1, 250, 0),
    ('FIRST_WIN', 'Win a game', 'WINS', 1, 500, 1),
    ('TEN_GAMES', 'Play 10 games', 'GAMES', 10, 500, 2),
    ('TEN_WINS', 'Win 10 games', 'WINS', 10, 1000, 3);
