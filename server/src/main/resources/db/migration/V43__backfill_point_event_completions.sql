-- Events without a completion bonus previously left only one transaction per
-- rewarded goal. If every goal has one, the last goal transaction is the
-- player's completion time.
INSERT INTO point_event_completions(event_id, user_id, completed_at)
SELECT achievement.event_id, point_tx.user_id, MAX(point_tx.created_at)
FROM point_event_achievements achievement
JOIN point_transactions point_tx
  ON point_tx.reference_id = achievement.id::text
 AND point_tx.transaction_type = 'EVENT_ACHIEVEMENT'
GROUP BY achievement.event_id, point_tx.user_id
HAVING COUNT(DISTINCT achievement.id) = (
    SELECT COUNT(*)
    FROM point_event_achievements expected
    WHERE expected.event_id = achievement.event_id
)
ON CONFLICT (event_id, user_id) DO NOTHING;
