CREATE UNIQUE INDEX IF NOT EXISTS uk_friend_requests_pending_pair
    ON friend_requests (
        LEAST(requester_user_id, recipient_user_id),
        GREATEST(requester_user_id, recipient_user_id)
    )
    WHERE status = 'PENDING';

CREATE INDEX IF NOT EXISTS idx_notifications_game_invite_lookup
    ON notifications (recipient_user_id, type, lobby_id);
