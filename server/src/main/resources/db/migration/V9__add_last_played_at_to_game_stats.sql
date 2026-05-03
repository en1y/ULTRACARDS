ALTER TABLE user_game_stats_entries
    ADD COLUMN IF NOT EXISTS last_played_at TIMESTAMP WITH TIME ZONE;

ALTER TABLE user_briskula_stats_entries
    ADD COLUMN IF NOT EXISTS last_played_at TIMESTAMP WITH TIME ZONE;

ALTER TABLE user_briskula_stats_wins_against_user
    ADD COLUMN IF NOT EXISTS last_played_at TIMESTAMP WITH TIME ZONE;

ALTER TABLE user_briskula_stats_wins_with_teammate
    ADD COLUMN IF NOT EXISTS last_played_at TIMESTAMP WITH TIME ZONE;
