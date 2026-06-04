CREATE TABLE IF NOT EXISTS chats (
    id UUID NOT NULL,
    friend_relation_id UUID,
    is_open BOOLEAN NOT NULL,
    CONSTRAINT pk_chats PRIMARY KEY (id),
    CONSTRAINT fk_chats_friend_relation
        FOREIGN KEY (friend_relation_id) REFERENCES friend_relations (id),
    CONSTRAINT uk_chats_friend_relation UNIQUE (friend_relation_id)
);

CREATE TABLE IF NOT EXISTS chat_messages (
    id UUID NOT NULL,
    chat_id UUID NOT NULL,
    sender_user_id BIGINT NOT NULL,
    message VARCHAR(200) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_chat_messages PRIMARY KEY (id),
    CONSTRAINT fk_chat_messages_chat
        FOREIGN KEY (chat_id) REFERENCES chats (id) ON DELETE CASCADE,
    CONSTRAINT fk_chat_messages_sender
        FOREIGN KEY (sender_user_id) REFERENCES users (id)
);

CREATE INDEX IF NOT EXISTS idx_chat_messages_chat_created
    ON chat_messages (chat_id, created_at ASC);

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
