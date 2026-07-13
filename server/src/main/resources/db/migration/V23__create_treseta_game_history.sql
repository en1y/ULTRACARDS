CREATE TABLE IF NOT EXISTS recorded_treseta_games
(
    id            UUID PRIMARY KEY REFERENCES recorded_games (id) ON DELETE CASCADE,
    game_config   VARCHAR(80) NOT NULL,
    teams_enabled BOOLEAN     NOT NULL
);

CREATE TABLE IF NOT EXISTS recorded_treseta_team_players
(
    game_id      UUID    NOT NULL REFERENCES recorded_treseta_games (id) ON DELETE CASCADE,
    user_id      BIGINT  NOT NULL,
    player_order INTEGER NOT NULL,
    PRIMARY KEY (game_id, player_order)
);
