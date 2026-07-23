CREATE INDEX IF NOT EXISTS idx_user_game_stats_entries_leaderboard
    ON user_game_stats_entries (game_type, played DESC, wins DESC);

CREATE INDEX IF NOT EXISTS idx_user_briskula_stats_entries_leaderboard
    ON user_briskula_stats_entries (briskula_game_config, played DESC, wins DESC);

CREATE INDEX IF NOT EXISTS idx_user_treseta_stats_entries_leaderboard
    ON user_treseta_stats_entries (treseta_game_config, played DESC, wins DESC);
