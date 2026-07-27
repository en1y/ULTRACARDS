-- Durak statistics. Per-mode counters are keyed by the canonical mode key, because Durak has
-- 168 valid configurations and an enum constant per combination is not maintainable.

CREATE TABLE IF NOT EXISTS user_durak_stats (
    id UUID NOT NULL,
    user_id BIGINT NOT NULL,
    times_durak INTEGER NOT NULL DEFAULT 0,
    draws INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT pk_user_durak_stats PRIMARY KEY (id),
    CONSTRAINT uk_user_durak_stats_user UNIQUE (user_id),
    CONSTRAINT fk_user_durak_stats_user
        FOREIGN KEY (user_id) REFERENCES users (id)
        ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS user_durak_stats_entries (
    user_durak_stats_id UUID NOT NULL,
    mode_key VARCHAR(64) NOT NULL,
    played INTEGER NOT NULL,
    wins INTEGER NOT NULL,
    last_played_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT pk_user_durak_stats_entries
        PRIMARY KEY (user_durak_stats_id, mode_key),
    CONSTRAINT fk_user_durak_stats_entries_stats
        FOREIGN KEY (user_durak_stats_id) REFERENCES user_durak_stats (id)
        ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_user_durak_stats_entries_stats_id
    ON user_durak_stats_entries (user_durak_stats_id);

CREATE INDEX IF NOT EXISTS idx_user_durak_stats_entries_leaderboard
    ON user_durak_stats_entries (mode_key, played DESC, wins DESC);

CREATE TABLE IF NOT EXISTS user_durak_stats_against_user (
    user_durak_stats_id UUID NOT NULL,
    mode_key VARCHAR(64) NOT NULL,
    related_user_id BIGINT NOT NULL,
    played INTEGER NOT NULL,
    wins INTEGER NOT NULL,
    last_played_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT pk_user_durak_stats_against_user
        PRIMARY KEY (user_durak_stats_id, mode_key, related_user_id),
    CONSTRAINT fk_user_durak_stats_against_user_stats
        FOREIGN KEY (user_durak_stats_id) REFERENCES user_durak_stats (id)
        ON DELETE CASCADE,
    CONSTRAINT fk_user_durak_stats_against_user_related_user
        FOREIGN KEY (related_user_id) REFERENCES users (id)
        ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_user_durak_stats_against_user_stats_id
    ON user_durak_stats_against_user (user_durak_stats_id);

CREATE INDEX IF NOT EXISTS idx_user_durak_stats_against_user_related_user_id
    ON user_durak_stats_against_user (related_user_id);
