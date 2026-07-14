-- Ordered history rows can contain gaps after legacy dependent-row cleanup.
-- Hibernate materializes those gaps as null list elements.

WITH ranked AS (
    SELECT game_id, player_order,
           row_number() OVER (PARTITION BY game_id ORDER BY player_order) - 1 AS new_order
    FROM recorded_game_players
)
UPDATE recorded_game_players players
SET player_order = 1000000 + ranked.new_order
FROM ranked
WHERE players.game_id = ranked.game_id
  AND players.player_order = ranked.player_order;

UPDATE recorded_game_players
SET player_order = player_order - 1000000;

WITH ranked AS (
    SELECT game_id, player_order,
           row_number() OVER (PARTITION BY game_id ORDER BY player_order) - 1 AS new_order
    FROM recorded_briskula_team_players
)
UPDATE recorded_briskula_team_players players
SET player_order = 1000000 + ranked.new_order
FROM ranked
WHERE players.game_id = ranked.game_id
  AND players.player_order = ranked.player_order;

UPDATE recorded_briskula_team_players
SET player_order = player_order - 1000000;

WITH ranked AS (
    SELECT game_id, player_order,
           row_number() OVER (PARTITION BY game_id ORDER BY player_order) - 1 AS new_order
    FROM recorded_treseta_team_players
)
UPDATE recorded_treseta_team_players players
SET player_order = 1000000 + ranked.new_order
FROM ranked
WHERE players.game_id = ranked.game_id
  AND players.player_order = ranked.player_order;

UPDATE recorded_treseta_team_players
SET player_order = player_order - 1000000;

WITH ranked AS (
    SELECT round_id, hand_order,
           row_number() OVER (PARTITION BY round_id ORDER BY hand_order) - 1 AS new_order
    FROM recorded_player_hands
)
UPDATE recorded_player_hands hands
SET hand_order = 1000000 + ranked.new_order
FROM ranked
WHERE hands.round_id = ranked.round_id
  AND hands.hand_order = ranked.hand_order;

UPDATE recorded_player_hands
SET hand_order = hand_order - 1000000;
