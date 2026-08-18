-- Daily goals used to count GAME_PLAYED / GAME_WON ledger rows, but finishing a
-- game no longer pays anything, so those rows are gone. One row per player per
-- game records the same facts without moving money; the primary key makes a
-- retried finalisation a no-op.
CREATE TABLE point_game_results (
    game_id UUID NOT NULL,
    user_id BIGINT NOT NULL,
    won BOOLEAN NOT NULL,
    ended_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_point_game_results PRIMARY KEY (game_id, user_id),
    CONSTRAINT fk_point_game_results_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE INDEX idx_point_game_results_user_ended
    ON point_game_results (user_id, ended_at DESC);

-- Backfill from the reward rows that are about to stop being written, so today's
-- goals do not reset for anyone mid-day.
INSERT INTO point_game_results (game_id, user_id, won, ended_at)
SELECT played.reference_id::uuid,
       played.user_id,
       EXISTS (SELECT 1 FROM point_transactions won
               WHERE won.user_id = played.user_id
                 AND won.transaction_type = 'GAME_WON'
                 AND won.reference_id = played.reference_id),
       played.created_at
FROM point_transactions played
WHERE played.transaction_type = 'GAME_PLAYED'
  AND played.reference_id ~ '^[0-9a-fA-F-]{36}$'
ON CONFLICT DO NOTHING;
