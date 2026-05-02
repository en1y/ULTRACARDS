ALTER TABLE user_game_stats_wins_against_user
    ADD COLUMN IF NOT EXISTS game_config VARCHAR(255) NOT NULL DEFAULT '';

ALTER TABLE user_game_stats_wins_with_teammate
    ADD COLUMN IF NOT EXISTS game_config VARCHAR(255) NOT NULL DEFAULT '';

ALTER TABLE user_game_stats_wins_against_user
    DROP CONSTRAINT IF EXISTS pk_user_game_stats_wins_against_user;

ALTER TABLE user_game_stats_wins_against_user
    ADD CONSTRAINT pk_user_game_stats_wins_against_user
        PRIMARY KEY (user_game_stats_id, game_type, game_config, related_user_id);

ALTER TABLE user_game_stats_wins_with_teammate
    DROP CONSTRAINT IF EXISTS pk_user_game_stats_wins_with_teammate;

ALTER TABLE user_game_stats_wins_with_teammate
    ADD CONSTRAINT pk_user_game_stats_wins_with_teammate
        PRIMARY KEY (user_game_stats_id, game_type, game_config, related_user_id);
