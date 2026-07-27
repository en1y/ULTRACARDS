-- Durak results may only name players captured in the immutable game recording.

ALTER TABLE recorded_game_players
    ADD CONSTRAINT uk_recorded_game_players_game_user UNIQUE (game_id, user_id);

-- Hibernate inserts the joined subtype before its element collections, so these checks must wait
-- until the transaction contains the corresponding recorded player rows.
ALTER TABLE recorded_durak_games
    ADD CONSTRAINT fk_recorded_durak_games_loser_player
        FOREIGN KEY (id, loser_user_id)
            REFERENCES recorded_game_players (game_id, user_id)
            DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE recorded_durak_finish_order
    ADD CONSTRAINT fk_recorded_durak_finish_order_player
        FOREIGN KEY (game_id, user_id)
            REFERENCES recorded_game_players (game_id, user_id)
            DEFERRABLE INITIALLY DEFERRED;
