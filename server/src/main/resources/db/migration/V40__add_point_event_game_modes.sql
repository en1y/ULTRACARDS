ALTER TABLE point_game_results
    ADD COLUMN game_mode VARCHAR(128);

ALTER TABLE point_event_achievements
    ADD COLUMN game_modes TEXT NOT NULL DEFAULT '';

CREATE INDEX idx_point_game_results_event_mode_progress
    ON point_game_results (user_id, game_type, game_mode, ended_at DESC, outcome);
