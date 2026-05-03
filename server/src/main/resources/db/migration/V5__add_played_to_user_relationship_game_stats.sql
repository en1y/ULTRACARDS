ALTER TABLE user_game_stats_wins_against_user
    ADD COLUMN IF NOT EXISTS played INTEGER NOT NULL DEFAULT 0;

UPDATE user_game_stats_wins_against_user
SET played = wins
WHERE played = 0;

ALTER TABLE user_game_stats_wins_with_teammate
    ADD COLUMN IF NOT EXISTS played INTEGER NOT NULL DEFAULT 0;

UPDATE user_game_stats_wins_with_teammate
SET played = wins
WHERE played = 0;
