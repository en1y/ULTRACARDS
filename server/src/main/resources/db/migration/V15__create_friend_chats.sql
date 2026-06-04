CREATE TABLE IF NOT EXISTS chats (
    id UUID NOT NULL,
    lobby_id UUID,
    friend_relation_id UUID,
    is_open BOOLEAN NOT NULL,
    CONSTRAINT pk_chats PRIMARY KEY (id),
    CONSTRAINT fk_chats_friend_relation
        FOREIGN KEY (friend_relation_id) REFERENCES friend_relations (id),
    CONSTRAINT uk_chats_friend_relation UNIQUE (friend_relation_id)
);

CREATE INDEX IF NOT EXISTS idx_chats_lobby
    ON chats (lobby_id);

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
