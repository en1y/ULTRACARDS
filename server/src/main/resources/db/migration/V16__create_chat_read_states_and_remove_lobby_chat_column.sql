DROP INDEX IF EXISTS idx_chats_lobby;

ALTER TABLE chats
    DROP COLUMN IF EXISTS lobby_id;

CREATE TABLE IF NOT EXISTS chat_read_states (
    id UUID NOT NULL,
    chat_id UUID NOT NULL,
    user_id BIGINT NOT NULL,
    last_read_message_id UUID,
    read_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_chat_read_states PRIMARY KEY (id),
    CONSTRAINT fk_chat_read_states_chat
        FOREIGN KEY (chat_id) REFERENCES chats (id) ON DELETE CASCADE,
    CONSTRAINT fk_chat_read_states_user
        FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_chat_read_states_last_read_message
        FOREIGN KEY (last_read_message_id) REFERENCES chat_messages (id) ON DELETE SET NULL,
    CONSTRAINT uk_chat_read_states_chat_user UNIQUE (chat_id, user_id)
);
