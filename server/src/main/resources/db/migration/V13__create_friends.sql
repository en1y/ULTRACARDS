CREATE TABLE IF NOT EXISTS friend_requests (
    id UUID NOT NULL,
    requester_user_id BIGINT NOT NULL,
    recipient_user_id BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    responded_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT pk_friend_requests PRIMARY KEY (id),
    CONSTRAINT fk_friend_requests_requester
        FOREIGN KEY (requester_user_id) REFERENCES users (id),
    CONSTRAINT fk_friend_requests_recipient
        FOREIGN KEY (recipient_user_id) REFERENCES users (id),
    CONSTRAINT ck_friend_requests_not_self
        CHECK (requester_user_id <> recipient_user_id)
);

CREATE INDEX IF NOT EXISTS idx_friend_requests_recipient_status_created
    ON friend_requests (recipient_user_id, status, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_friend_requests_requester_status_created
    ON friend_requests (requester_user_id, status, created_at DESC);

CREATE TABLE IF NOT EXISTS friend_relations (
    id UUID NOT NULL,
    user_one_id BIGINT NOT NULL,
    user_two_id BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL,
    removed_by_user_id BIGINT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    removed_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT pk_friend_relations PRIMARY KEY (id),
    CONSTRAINT fk_friend_relations_user_one
        FOREIGN KEY (user_one_id) REFERENCES users (id),
    CONSTRAINT fk_friend_relations_user_two
        FOREIGN KEY (user_two_id) REFERENCES users (id),
    CONSTRAINT fk_friend_relations_removed_by
        FOREIGN KEY (removed_by_user_id) REFERENCES users (id),
    CONSTRAINT uk_friend_relations_user_pair UNIQUE (user_one_id, user_two_id),
    CONSTRAINT ck_friend_relations_normalized_pair
        CHECK (user_one_id < user_two_id)
);

CREATE INDEX IF NOT EXISTS idx_friend_relations_user_one_status
    ON friend_relations (user_one_id, status);

CREATE INDEX IF NOT EXISTS idx_friend_relations_user_two_status
    ON friend_relations (user_two_id, status);

CREATE TABLE IF NOT EXISTS friend_blocks (
    id UUID NOT NULL,
    blocker_user_id BIGINT NOT NULL,
    blocked_user_id BIGINT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_friend_blocks PRIMARY KEY (id),
    CONSTRAINT fk_friend_blocks_blocker
        FOREIGN KEY (blocker_user_id) REFERENCES users (id),
    CONSTRAINT fk_friend_blocks_blocked
        FOREIGN KEY (blocked_user_id) REFERENCES users (id),
    CONSTRAINT uk_friend_blocks_pair UNIQUE (blocker_user_id, blocked_user_id),
    CONSTRAINT ck_friend_blocks_not_self
        CHECK (blocker_user_id <> blocked_user_id)
);

CREATE INDEX IF NOT EXISTS idx_friend_blocks_blocked
    ON friend_blocks (blocked_user_id);

ALTER TABLE notifications
    ADD COLUMN IF NOT EXISTS friend_request_id UUID;

ALTER TABLE notifications
    ADD CONSTRAINT fk_notifications_friend_request
        FOREIGN KEY (friend_request_id) REFERENCES friend_requests (id);

CREATE INDEX IF NOT EXISTS idx_notifications_friend_request
    ON notifications (friend_request_id);
