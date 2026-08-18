CREATE TABLE point_event_completions (
    event_id UUID NOT NULL,
    user_id BIGINT NOT NULL,
    completed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_point_event_completions PRIMARY KEY (event_id, user_id),
    CONSTRAINT fk_point_event_completions_event FOREIGN KEY (event_id) REFERENCES point_events (id) ON DELETE CASCADE,
    CONSTRAINT fk_point_event_completions_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE INDEX idx_point_event_completions_user
    ON point_event_completions (user_id, completed_at DESC);

INSERT INTO point_event_completions(event_id, user_id, completed_at)
SELECT event.id, point_tx.user_id, MIN(point_tx.created_at)
FROM point_transactions point_tx
JOIN point_events event ON event.id::text = point_tx.reference_id
WHERE point_tx.transaction_type = 'EVENT_COMPLETION'
GROUP BY event.id, point_tx.user_id;
