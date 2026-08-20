-- Daily play streaks and the one-time achievements they feed.
--
-- The streak is derived from point_game_results the first time (below), then
-- owned by this table: recomputing a 3-year run from the ledger on every game
-- finish would scan the whole history for nothing.

CREATE TABLE user_streaks (
    user_id BIGINT NOT NULL,
    current_streak INTEGER NOT NULL DEFAULT 0,
    longest_streak INTEGER NOT NULL DEFAULT 0,
    last_played_date DATE,
    streak_freezes INTEGER NOT NULL DEFAULT 0,
    -- The streak length that last paid a freeze, so one 7-day mark never pays twice.
    freezes_paid_through INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT pk_user_streaks PRIMARY KEY (user_id),
    CONSTRAINT fk_user_streaks_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT chk_user_streaks_current CHECK (current_streak >= 0),
    CONSTRAINT chk_user_streaks_longest CHECK (longest_streak >= 0),
    CONSTRAINT chk_user_streaks_freezes CHECK (streak_freezes BETWEEN 0 AND 3),
    CONSTRAINT chk_user_streaks_paid_through CHECK (freezes_paid_through >= 0)
);

-- One-time milestones, edited by admins the same way daily goals are.
CREATE TABLE point_achievements (
    code VARCHAR(64) NOT NULL,
    name VARCHAR(120) NOT NULL,
    metric VARCHAR(16) NOT NULL,
    target BIGINT NOT NULL,
    reward_points BIGINT NOT NULL DEFAULT 0,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    sort_order INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT pk_point_achievements PRIMARY KEY (code),
    CONSTRAINT chk_point_achievements_name CHECK (btrim(name) <> ''),
    CONSTRAINT chk_point_achievements_metric CHECK (metric IN ('STREAK', 'GAMES', 'WINS')),
    CONSTRAINT chk_point_achievements_target CHECK (target > 0),
    CONSTRAINT chk_point_achievements_reward CHECK (reward_points >= 0)
);

-- Completion is tracked here rather than inferred from the ledger: a 0-Point
-- achievement writes no transaction (amount <> 0 is enforced), and an admin
-- editing a reward later must not resurrect an already-earned milestone.
CREATE TABLE user_achievements (
    user_id BIGINT NOT NULL,
    code VARCHAR(64) NOT NULL,
    earned_at TIMESTAMP WITH TIME ZONE NOT NULL,
    reward_points BIGINT NOT NULL,
    CONSTRAINT pk_user_achievements PRIMARY KEY (user_id, code),
    CONSTRAINT fk_user_achievements_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT chk_user_achievements_reward CHECK (reward_points >= 0)
);

CREATE INDEX idx_user_achievements_user_earned
    ON user_achievements (user_id, earned_at DESC);

INSERT INTO point_achievements(code, name, metric, target, reward_points, sort_order) VALUES
    ('STREAK_3',    'Play 3 days in a row',    'STREAK',    3,     500,  0),
    ('STREAK_7',    'Play 7 days in a row',    'STREAK',    7,    1500,  1),
    ('STREAK_14',   'Play 14 days in a row',   'STREAK',   14,    3000,  2),
    ('STREAK_30',   'Play 30 days in a row',   'STREAK',   30,    7500,  3),
    ('STREAK_100',  'Play 100 days in a row',  'STREAK',  100,   25000,  4),
    ('STREAK_200',  'Play 200 days in a row',  'STREAK',  200,   60000,  5),
    ('STREAK_300',  'Play 300 days in a row',  'STREAK',  300,  100000,  6),
    ('STREAK_365',  'Play a full year in a row', 'STREAK', 365,  150000,  7),
    ('STREAK_730',  'Play two years in a row',  'STREAK',  730,  500000,  8),
    ('STREAK_1095', 'Play three years in a row', 'STREAK', 1095, 1000000, 9),
    ('GAMES_10',    'Finish 10 games',    'GAMES',   10,    500, 10),
    ('GAMES_50',    'Finish 50 games',    'GAMES',   50,   2000, 11),
    ('GAMES_100',   'Finish 100 games',   'GAMES',  100,   5000, 12),
    ('GAMES_500',   'Finish 500 games',   'GAMES',  500,  20000, 13),
    ('GAMES_1000',  'Finish 1000 games',  'GAMES', 1000,  50000, 14),
    ('WINS_10',     'Win 10 games',    'WINS',   10,   1000, 15),
    ('WINS_50',     'Win 50 games',    'WINS',   50,   4000, 16),
    ('WINS_100',    'Win 100 games',   'WINS',  100,  10000, 17),
    ('WINS_500',    'Win 500 games',   'WINS',  500,  40000, 18),
    ('WINS_1000',   'Win 1000 games',  'WINS', 1000, 100000, 19);

-- Seed every existing player's streak from the games they have already finished.
-- Consecutive dates share `played_on - row_number`, which turns each unbroken run
-- into one group; the run touching today or yesterday is the live streak.
WITH played_days AS (
    SELECT user_id, (ended_at AT TIME ZONE 'Europe/Zagreb')::date AS played_on
    FROM point_game_results
    GROUP BY user_id, (ended_at AT TIME ZONE 'Europe/Zagreb')::date
),
runs AS (
    SELECT user_id,
           COUNT(*) AS length,
           MAX(played_on) AS ended_on
    FROM (
        SELECT user_id, played_on,
               played_on - (ROW_NUMBER() OVER (PARTITION BY user_id ORDER BY played_on))::int AS run_key
        FROM played_days
    ) grouped
    GROUP BY user_id, run_key
),
seeded AS (
    SELECT user_id,
           COALESCE(MAX(length) FILTER (
               WHERE ended_on >= (now() AT TIME ZONE 'Europe/Zagreb')::date - 1), 0) AS current_streak,
           MAX(length) AS longest_streak,
           MAX(ended_on) AS last_played_date
    FROM runs
    GROUP BY user_id
)
INSERT INTO user_streaks (user_id, current_streak, longest_streak, last_played_date,
                          streak_freezes, freezes_paid_through)
SELECT user_id, current_streak, longest_streak, last_played_date,
       -- Hand over the freezes the run would have earned, so nobody loses a
       -- long streak to the day this shipped.
       LEAST(3, current_streak / 7),
       current_streak - (current_streak % 7)
FROM seeded;
