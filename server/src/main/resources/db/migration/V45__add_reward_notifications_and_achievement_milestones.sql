-- Rich completion notifications keep their reward metadata after reconnects, so the
-- browser can render the same celebration from both REST and WebSocket payloads.
ALTER TABLE notifications
    ADD COLUMN reward_points BIGINT;

ALTER TABLE notifications
    ADD CONSTRAINT chk_notifications_reward_points
        CHECK (reward_points IS NULL OR reward_points >= 0);

-- Add the requested gaps to the default ladders while preserving the relative order
-- of every existing seeded achievement.
UPDATE point_achievements
SET sort_order = sort_order + 1
WHERE code IN ('STREAK_100', 'STREAK_200', 'STREAK_300', 'STREAK_365', 'STREAK_730', 'STREAK_1095');

UPDATE point_achievements
SET sort_order = sort_order + CASE WHEN code = 'GAMES_1000' THEN 2 ELSE 1 END
WHERE code IN ('GAMES_500', 'GAMES_1000');

UPDATE point_achievements
SET sort_order = sort_order + 2
WHERE code IN ('WINS_10', 'WINS_50', 'WINS_100', 'WINS_500', 'WINS_1000');

INSERT INTO point_achievements(code, name, metric, target, reward_points, sort_order) VALUES
    ('STREAK_50', 'Play 50 days in a row', 'STREAK', 50, 15000, 4),
    ('GAMES_250', 'Finish 250 games', 'GAMES', 250, 10000, 13),
    ('GAMES_750', 'Finish 750 games', 'GAMES', 750, 35000, 15)
ON CONFLICT (code) DO NOTHING;
