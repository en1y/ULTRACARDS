CREATE TABLE IF NOT EXISTS user_game_stats_wins_against_user (
    user_game_stats_id UUID NOT NULL,
    game_type VARCHAR(255) NOT NULL,
    related_user_id BIGINT NOT NULL,
    wins INTEGER NOT NULL,
    CONSTRAINT pk_user_game_stats_wins_against_user
        PRIMARY KEY (user_game_stats_id, game_type, related_user_id),
    CONSTRAINT fk_user_game_stats_wins_against_user_stats
        FOREIGN KEY (user_game_stats_id) REFERENCES user_game_stats (id)
        ON DELETE CASCADE,
    CONSTRAINT fk_user_game_stats_wins_against_user_related_user
        FOREIGN KEY (related_user_id) REFERENCES users (id)
        ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_user_game_stats_wins_against_user_stats_id
    ON user_game_stats_wins_against_user (user_game_stats_id);

CREATE INDEX IF NOT EXISTS idx_user_game_stats_wins_against_user_related_user_id
    ON user_game_stats_wins_against_user (related_user_id);

CREATE TABLE IF NOT EXISTS user_game_stats_wins_with_teammate (
    user_game_stats_id UUID NOT NULL,
    game_type VARCHAR(255) NOT NULL,
    related_user_id BIGINT NOT NULL,
    wins INTEGER NOT NULL,
    CONSTRAINT pk_user_game_stats_wins_with_teammate
        PRIMARY KEY (user_game_stats_id, game_type, related_user_id),
    CONSTRAINT fk_user_game_stats_wins_with_teammate_stats
        FOREIGN KEY (user_game_stats_id) REFERENCES user_game_stats (id)
        ON DELETE CASCADE,
    CONSTRAINT fk_user_game_stats_wins_with_teammate_related_user
        FOREIGN KEY (related_user_id) REFERENCES users (id)
        ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_user_game_stats_wins_with_teammate_stats_id
    ON user_game_stats_wins_with_teammate (user_game_stats_id);

CREATE INDEX IF NOT EXISTS idx_user_game_stats_wins_with_teammate_related_user_id
    ON user_game_stats_wins_with_teammate (related_user_id);
