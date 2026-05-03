CREATE TABLE IF NOT EXISTS user_briskula_game_stats_entries (
    user_game_stats_id UUID NOT NULL,
    briskula_game_config VARCHAR(255) NOT NULL,
    played INTEGER NOT NULL,
    wins INTEGER NOT NULL,
    CONSTRAINT pk_user_briskula_game_stats_entries
        PRIMARY KEY (user_game_stats_id, briskula_game_config),
    CONSTRAINT fk_user_briskula_game_stats_entries_user_game_stats
        FOREIGN KEY (user_game_stats_id) REFERENCES user_game_stats (id)
        ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_user_briskula_game_stats_entries_user_game_stats_id
    ON user_briskula_game_stats_entries (user_game_stats_id);
