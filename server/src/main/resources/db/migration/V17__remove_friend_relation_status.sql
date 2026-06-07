DELETE FROM chats
WHERE friend_relation_id IN (
    SELECT id
    FROM friend_relations
    WHERE status <> 'FRIENDS'
);

DELETE FROM friend_relations
WHERE status <> 'FRIENDS';

DROP INDEX IF EXISTS idx_friend_relations_user_one_status;
DROP INDEX IF EXISTS idx_friend_relations_user_two_status;

ALTER TABLE friend_relations
    DROP CONSTRAINT IF EXISTS fk_friend_relations_removed_by,
    DROP COLUMN IF EXISTS status,
    DROP COLUMN IF EXISTS removed_by_user_id,
    DROP COLUMN IF EXISTS removed_at;

CREATE INDEX IF NOT EXISTS idx_friend_relations_user_one
    ON friend_relations (user_one_id);

CREATE INDEX IF NOT EXISTS idx_friend_relations_user_two
    ON friend_relations (user_two_id);
