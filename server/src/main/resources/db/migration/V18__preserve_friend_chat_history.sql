ALTER TABLE chats
    ADD COLUMN user_one_id BIGINT,
    ADD COLUMN user_two_id BIGINT;

UPDATE chats chat
SET user_one_id = relation.user_one_id,
    user_two_id = relation.user_two_id
FROM friend_relations relation
WHERE chat.friend_relation_id = relation.id
  AND (chat.user_one_id IS NULL OR chat.user_two_id IS NULL);

ALTER TABLE chats
    ADD CONSTRAINT fk_chats_user_one
        FOREIGN KEY (user_one_id) REFERENCES users (id),
    ADD CONSTRAINT fk_chats_user_two
        FOREIGN KEY (user_two_id) REFERENCES users (id),
    ADD CONSTRAINT uk_chats_user_pair UNIQUE (user_one_id, user_two_id),
    ADD CONSTRAINT ck_chats_normalized_user_pair
        CHECK (
            (user_one_id IS NULL AND user_two_id IS NULL)
            OR (user_one_id IS NOT NULL AND user_two_id IS NOT NULL AND user_one_id < user_two_id)
        );

CREATE INDEX IF NOT EXISTS idx_chats_user_one
    ON chats (user_one_id);

CREATE INDEX IF NOT EXISTS idx_chats_user_two
    ON chats (user_two_id);
