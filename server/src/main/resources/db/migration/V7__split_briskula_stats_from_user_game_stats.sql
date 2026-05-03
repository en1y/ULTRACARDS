CREATE TABLE IF NOT EXISTS user_briskula_stats (
    id UUID NOT NULL,
    user_id BIGINT NOT NULL,
    CONSTRAINT pk_user_briskula_stats PRIMARY KEY (id),
    CONSTRAINT uk_user_briskula_stats_user UNIQUE (user_id),
    CONSTRAINT fk_user_briskula_stats_user
        FOREIGN KEY (user_id) REFERENCES users (id)
        ON DELETE CASCADE
);

INSERT INTO user_briskula_stats (id, user_id)
SELECT ugs.id, ugs.user_id
FROM user_game_stats ugs
LEFT JOIN user_briskula_stats ubs ON ubs.user_id = ugs.user_id
WHERE ubs.user_id IS NULL;

CREATE TABLE IF NOT EXISTS user_briskula_stats_entries (
    user_briskula_stats_id UUID NOT NULL,
    briskula_game_config VARCHAR(255) NOT NULL,
    played INTEGER NOT NULL,
    wins INTEGER NOT NULL,
    CONSTRAINT pk_user_briskula_stats_entries
        PRIMARY KEY (user_briskula_stats_id, briskula_game_config),
    CONSTRAINT fk_user_briskula_stats_entries_stats
        FOREIGN KEY (user_briskula_stats_id) REFERENCES user_briskula_stats (id)
        ON DELETE CASCADE
);

INSERT INTO user_briskula_stats_entries (user_briskula_stats_id, briskula_game_config, played, wins)
SELECT ubs.id, ubgse.briskula_game_config, ubgse.played, ubgse.wins
FROM user_briskula_game_stats_entries ubgse
JOIN user_briskula_stats ubs ON ubs.id = ubgse.user_game_stats_id
LEFT JOIN user_briskula_stats_entries ubse
    ON ubse.user_briskula_stats_id = ubs.id
   AND ubse.briskula_game_config = ubgse.briskula_game_config
WHERE ubse.user_briskula_stats_id IS NULL;

CREATE INDEX IF NOT EXISTS idx_user_briskula_stats_entries_stats_id
    ON user_briskula_stats_entries (user_briskula_stats_id);

CREATE TABLE IF NOT EXISTS user_briskula_stats_wins_against_user (
    user_briskula_stats_id UUID NOT NULL,
    game_config VARCHAR(255) NOT NULL,
    related_user_id BIGINT NOT NULL,
    played INTEGER NOT NULL,
    wins INTEGER NOT NULL,
    CONSTRAINT pk_user_briskula_stats_wins_against_user
        PRIMARY KEY (user_briskula_stats_id, game_config, related_user_id),
    CONSTRAINT fk_user_briskula_stats_wins_against_user_stats
        FOREIGN KEY (user_briskula_stats_id) REFERENCES user_briskula_stats (id)
        ON DELETE CASCADE,
    CONSTRAINT fk_user_briskula_stats_wins_against_user_related_user
        FOREIGN KEY (related_user_id) REFERENCES users (id)
        ON DELETE CASCADE
);

INSERT INTO user_briskula_stats_wins_against_user (user_briskula_stats_id, game_config, related_user_id, played, wins)
SELECT ubs.id, ugswau.game_config, ugswau.related_user_id, ugswau.played, ugswau.wins
FROM user_game_stats_wins_against_user ugswau
JOIN user_briskula_stats ubs ON ubs.id = ugswau.user_game_stats_id
LEFT JOIN user_briskula_stats_wins_against_user ubswau
    ON ubswau.user_briskula_stats_id = ubs.id
   AND ubswau.game_config = ugswau.game_config
   AND ubswau.related_user_id = ugswau.related_user_id
WHERE ugswau.game_type = 'BRISKULA'
  AND ubswau.user_briskula_stats_id IS NULL;

CREATE INDEX IF NOT EXISTS idx_user_briskula_stats_wins_against_user_stats_id
    ON user_briskula_stats_wins_against_user (user_briskula_stats_id);

CREATE INDEX IF NOT EXISTS idx_user_briskula_stats_wins_against_user_related_user_id
    ON user_briskula_stats_wins_against_user (related_user_id);

CREATE TABLE IF NOT EXISTS user_briskula_stats_wins_with_teammate (
    user_briskula_stats_id UUID NOT NULL,
    game_config VARCHAR(255) NOT NULL,
    related_user_id BIGINT NOT NULL,
    played INTEGER NOT NULL,
    wins INTEGER NOT NULL,
    CONSTRAINT pk_user_briskula_stats_wins_with_teammate
        PRIMARY KEY (user_briskula_stats_id, game_config, related_user_id),
    CONSTRAINT fk_user_briskula_stats_wins_with_teammate_stats
        FOREIGN KEY (user_briskula_stats_id) REFERENCES user_briskula_stats (id)
        ON DELETE CASCADE,
    CONSTRAINT fk_user_briskula_stats_wins_with_teammate_related_user
        FOREIGN KEY (related_user_id) REFERENCES users (id)
        ON DELETE CASCADE
);

INSERT INTO user_briskula_stats_wins_with_teammate (user_briskula_stats_id, game_config, related_user_id, played, wins)
SELECT ubs.id, ugswwt.game_config, ugswwt.related_user_id, ugswwt.played, ugswwt.wins
FROM user_game_stats_wins_with_teammate ugswwt
JOIN user_briskula_stats ubs ON ubs.id = ugswwt.user_game_stats_id
LEFT JOIN user_briskula_stats_wins_with_teammate ubswwt
    ON ubswwt.user_briskula_stats_id = ubs.id
   AND ubswwt.game_config = ugswwt.game_config
   AND ubswwt.related_user_id = ugswwt.related_user_id
WHERE ugswwt.game_type = 'BRISKULA'
  AND ubswwt.user_briskula_stats_id IS NULL;

CREATE INDEX IF NOT EXISTS idx_user_briskula_stats_wins_with_teammate_stats_id
    ON user_briskula_stats_wins_with_teammate (user_briskula_stats_id);

CREATE INDEX IF NOT EXISTS idx_user_briskula_stats_wins_with_teammate_related_user_id
    ON user_briskula_stats_wins_with_teammate (related_user_id);
