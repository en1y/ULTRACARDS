CREATE TABLE IF NOT EXISTS user_treseta_stats (
    id UUID NOT NULL,
    user_id BIGINT NOT NULL,
    CONSTRAINT pk_user_treseta_stats PRIMARY KEY (id),
    CONSTRAINT uk_user_treseta_stats_user UNIQUE (user_id),
    CONSTRAINT fk_user_treseta_stats_user
        FOREIGN KEY (user_id) REFERENCES users (id)
        ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS user_treseta_stats_entries (
    user_treseta_stats_id UUID NOT NULL,
    treseta_game_config VARCHAR(255) NOT NULL,
    played INTEGER NOT NULL,
    wins INTEGER NOT NULL,
    last_played_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT pk_user_treseta_stats_entries
        PRIMARY KEY (user_treseta_stats_id, treseta_game_config),
    CONSTRAINT fk_user_treseta_stats_entries_stats
        FOREIGN KEY (user_treseta_stats_id) REFERENCES user_treseta_stats (id)
        ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_user_treseta_stats_entries_stats_id
    ON user_treseta_stats_entries (user_treseta_stats_id);

CREATE TABLE IF NOT EXISTS user_treseta_stats_wins_against_user (
    user_treseta_stats_id UUID NOT NULL,
    game_config VARCHAR(255) NOT NULL,
    related_user_id BIGINT NOT NULL,
    played INTEGER NOT NULL,
    wins INTEGER NOT NULL,
    last_played_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT pk_user_treseta_stats_wins_against_user
        PRIMARY KEY (user_treseta_stats_id, game_config, related_user_id),
    CONSTRAINT fk_user_treseta_stats_wins_against_user_stats
        FOREIGN KEY (user_treseta_stats_id) REFERENCES user_treseta_stats (id)
        ON DELETE CASCADE,
    CONSTRAINT fk_user_treseta_stats_wins_against_user_related_user
        FOREIGN KEY (related_user_id) REFERENCES users (id)
        ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_user_treseta_stats_wins_against_user_stats_id
    ON user_treseta_stats_wins_against_user (user_treseta_stats_id);

CREATE INDEX IF NOT EXISTS idx_user_treseta_stats_wins_against_user_related_user_id
    ON user_treseta_stats_wins_against_user (related_user_id);

CREATE TABLE IF NOT EXISTS user_treseta_stats_wins_with_teammate (
    user_treseta_stats_id UUID NOT NULL,
    game_config VARCHAR(255) NOT NULL,
    related_user_id BIGINT NOT NULL,
    played INTEGER NOT NULL,
    wins INTEGER NOT NULL,
    last_played_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT pk_user_treseta_stats_wins_with_teammate
        PRIMARY KEY (user_treseta_stats_id, game_config, related_user_id),
    CONSTRAINT fk_user_treseta_stats_wins_with_teammate_stats
        FOREIGN KEY (user_treseta_stats_id) REFERENCES user_treseta_stats (id)
        ON DELETE CASCADE,
    CONSTRAINT fk_user_treseta_stats_wins_with_teammate_related_user
        FOREIGN KEY (related_user_id) REFERENCES users (id)
        ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_user_treseta_stats_wins_with_teammate_stats_id
    ON user_treseta_stats_wins_with_teammate (user_treseta_stats_id);

CREATE INDEX IF NOT EXISTS idx_user_treseta_stats_wins_with_teammate_related_user_id
    ON user_treseta_stats_wins_with_teammate (related_user_id);
