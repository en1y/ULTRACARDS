INSERT INTO recorded_games (id, lobby_id, name, owner_user_id, created_at, started_at, ended_at)
SELECT id, lobby_id, name, owner_id, created_at, created_at, ended_at
FROM briskula_games;

INSERT INTO recorded_briskula_games (id, game_config, teams_enabled, trump_suit, trump_value)
SELECT id, game_config, game_config = 'FOUR_PLAYERS_WITH_TEAMS', trump_card_suit, trump_card_value
FROM briskula_games;

INSERT INTO recorded_game_players (game_id, user_id, name, player_order)
SELECT player.briskula_game_id, player.user_id, user_entity.username, player.player_order
FROM briskula_game_players player
JOIN users user_entity ON user_entity.id = player.user_id;

INSERT INTO recorded_briskula_team_players (game_id, user_id, player_order)
SELECT briskula_game_id, user_id, player_order
FROM briskula_game_team_players;

CREATE TEMP TABLE briskula_round_mapping
(
    legacy_id   BIGINT PRIMARY KEY,
    recorded_id BIGINT NOT NULL
) ON COMMIT DROP;

WITH inserted_rounds AS (
    INSERT INTO recorded_rounds (game_id, round_order, user_id, name)
    SELECT field.briskula_game_id, field.field_order, field.winner_user_id, winner.username
    FROM briskula_playing_fields field
    LEFT JOIN users winner ON winner.id = field.winner_user_id
    RETURNING id, game_id, round_order
)
INSERT INTO briskula_round_mapping (legacy_id, recorded_id)
SELECT field.id, inserted_rounds.id
FROM briskula_playing_fields field
JOIN inserted_rounds ON inserted_rounds.game_id = field.briskula_game_id
    AND inserted_rounds.round_order = field.field_order;

INSERT INTO recorded_round_attributes (round_id, attribute_key, attribute_value)
SELECT mapping.recorded_id, 'points', field.total_points::text
FROM briskula_playing_fields field
JOIN briskula_round_mapping mapping ON mapping.legacy_id = field.id
WHERE field.total_points IS NOT NULL;

INSERT INTO recorded_plays (round_id, play_order, user_id, name, suit, value, number)
SELECT mapping.recorded_id,
       play.ord - 1,
       play.user_id::bigint,
       user_entity.username,
       CASE left(play.card, 1)
           WHEN 'C' THEN 'COPPE'
           WHEN 'D' THEN 'DENARI'
           WHEN 'S' THEN 'SPADE'
           WHEN 'B' THEN 'BASTONI'
       END,
       CASE substring(play.card FROM 2)::integer
           WHEN 1 THEN 'ACE'
           WHEN 2 THEN 'TWO'
           WHEN 3 THEN 'THREE'
           WHEN 4 THEN 'FOUR'
           WHEN 5 THEN 'FIVE'
           WHEN 6 THEN 'SIX'
           WHEN 7 THEN 'SEVEN'
           WHEN 11 THEN 'JACK'
           WHEN 12 THEN 'KNIGHT'
           WHEN 13 THEN 'KING'
       END,
       substring(play.card FROM 2)::integer
FROM briskula_playing_fields field
JOIN briskula_round_mapping mapping ON mapping.legacy_id = field.id
CROSS JOIN LATERAL unnest(
        string_to_array(NULLIF(field.played_cards, ''), ','),
        string_to_array(NULLIF(field.played_player_ids, ''), ',')
    ) WITH ORDINALITY AS play(card, user_id, ord)
JOIN users user_entity ON user_entity.id = play.user_id::bigint;

INSERT INTO recorded_player_hands (round_id, hand_order, user_id, name)
SELECT mapping.recorded_id, hand.ord - 1, split_part(hand.entry, ':', 1)::bigint, user_entity.username
FROM briskula_playing_fields field
JOIN briskula_round_mapping mapping ON mapping.legacy_id = field.id
CROSS JOIN LATERAL regexp_split_to_table(NULLIF(field.player_hands, ''), '\|') WITH ORDINALITY AS hand(entry, ord)
JOIN users user_entity ON user_entity.id = split_part(hand.entry, ':', 1)::bigint;

INSERT INTO recorded_hand_cards (hand_id, suit, value, number)
SELECT recorded_hand.id,
       CASE left(card.card, 1)
           WHEN 'C' THEN 'COPPE'
           WHEN 'D' THEN 'DENARI'
           WHEN 'S' THEN 'SPADE'
           WHEN 'B' THEN 'BASTONI'
       END,
       CASE substring(card.card FROM 2)::integer
           WHEN 1 THEN 'ACE'
           WHEN 2 THEN 'TWO'
           WHEN 3 THEN 'THREE'
           WHEN 4 THEN 'FOUR'
           WHEN 5 THEN 'FIVE'
           WHEN 6 THEN 'SIX'
           WHEN 7 THEN 'SEVEN'
           WHEN 11 THEN 'JACK'
           WHEN 12 THEN 'KNIGHT'
           WHEN 13 THEN 'KING'
       END,
       substring(card.card FROM 2)::integer
FROM briskula_playing_fields field
JOIN briskula_round_mapping mapping ON mapping.legacy_id = field.id
CROSS JOIN LATERAL regexp_split_to_table(NULLIF(field.player_hands, ''), '\|') WITH ORDINALITY AS hand(entry, hand_order)
CROSS JOIN LATERAL regexp_split_to_table(NULLIF(split_part(hand.entry, ':', 2), ''), '-') AS card(card)
JOIN recorded_player_hands recorded_hand ON recorded_hand.round_id = mapping.recorded_id AND recorded_hand.hand_order = hand.hand_order - 1;
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = current_schema() AND table_name = 'recorded_rounds' AND column_name = 'winner_user_id'
    ) AND NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = current_schema() AND table_name = 'recorded_rounds' AND column_name = 'user_id'
    ) THEN
        ALTER TABLE recorded_rounds RENAME COLUMN winner_user_id TO user_id;
    END IF;

    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = current_schema() AND table_name = 'recorded_rounds' AND column_name = 'winner_name'
    ) AND NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = current_schema() AND table_name = 'recorded_rounds' AND column_name = 'name'
    ) THEN
        ALTER TABLE recorded_rounds RENAME COLUMN winner_name TO name;
    END IF;
END $$;
