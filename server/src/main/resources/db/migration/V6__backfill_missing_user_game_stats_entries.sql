CREATE TABLE IF NOT EXISTS user_game_stats_entries (
    user_game_stats_id UUID NOT NULL,
    game_type VARCHAR(255) NOT NULL,
    played INTEGER NOT NULL,
    wins INTEGER NOT NULL,
    CONSTRAINT pk_user_game_stats_entries PRIMARY KEY (user_game_stats_id, game_type),
    CONSTRAINT fk_user_game_stats_entries_user_game_stats
        FOREIGN KEY (user_game_stats_id) REFERENCES user_game_stats (id)
        ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_user_game_stats_entries_user_game_stats_id
    ON user_game_stats_entries (user_game_stats_id);

INSERT INTO user_game_stats_entries (user_game_stats_id, game_type, played, wins)
SELECT ugs.id, game_types.game_type, 0, 0
FROM user_game_stats ugs
CROSS JOIN (
    VALUES
        ('BRISKULA'),
        ('POKER'),
        ('TRESETA'),
        ('DURAK')
) AS game_types(game_type)
LEFT JOIN user_game_stats_entries ugse
    ON ugse.user_game_stats_id = ugs.id
   AND ugse.game_type = game_types.game_type
WHERE ugse.user_game_stats_id IS NULL;
