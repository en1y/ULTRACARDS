INSERT INTO user_game_stats_entries (user_game_stats_id, game_type, played, wins)
SELECT ugs.id, game_types.game_type, 0, 0
FROM user_game_stats ugs
CROSS JOIN (
    VALUES
        ('BRISKULA'),
        ('POKER'),
        ('TRESETA'),
        ('DURAK')
) AS game_types(game_type)
LEFT JOIN user_game_stats_entries ugse
    ON ugse.user_game_stats_id = ugs.id
   AND ugse.game_type = game_types.game_type
WHERE ugse.user_game_stats_id IS NULL;
