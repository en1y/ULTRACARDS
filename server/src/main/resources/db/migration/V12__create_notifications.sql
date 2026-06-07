CREATE TABLE IF NOT EXISTS notifications (
    id UUID NOT NULL,
    recipient_user_id BIGINT NOT NULL,
    sender_user_id BIGINT,
    type VARCHAR(32) NOT NULL,
    message VARCHAR(512),
    lobby_id UUID,
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    read_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT pk_notifications PRIMARY KEY (id),
    CONSTRAINT fk_notifications_recipient_user
        FOREIGN KEY (recipient_user_id) REFERENCES users (id),
    CONSTRAINT fk_notifications_sender_user
        FOREIGN KEY (sender_user_id) REFERENCES users (id)
);

CREATE INDEX IF NOT EXISTS idx_notifications_recipient_created_at
    ON notifications (recipient_user_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_notifications_recipient_read_created_at
    ON notifications (recipient_user_id, is_read, created_at DESC);
