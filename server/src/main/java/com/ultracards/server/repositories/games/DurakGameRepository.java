package com.ultracards.server.repositories.games;

import com.ultracards.recorder.RecordedDurakGame;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface DurakGameRepository extends JpaRepository<RecordedDurakGame, UUID> {
    long countByEndedAtIsNull();
    long countByEndedAtIsNotNull();

    @Query(value = """
            SELECT game.*, durak.mode_key, durak.number_of_players, durak.deck_size, durak.jokers_enabled,
                   durak.throw_in_policy, durak.passing_enabled, durak.trump_suit, durak.trump_indicator_code,
                   durak.loser_user_id, durak.draw
            FROM recorded_durak_games durak JOIN recorded_games game ON game.id = durak.id
            JOIN recorded_game_players player ON player.game_id = game.id
            WHERE player.user_id = :userId ORDER BY game.ended_at DESC
            """, nativeQuery = true)
    List<RecordedDurakGame> findPastGamesByUserIdLatest(Long userId);

    @Query(value = """
            SELECT game.*, durak.mode_key, durak.number_of_players, durak.deck_size, durak.jokers_enabled,
                   durak.throw_in_policy, durak.passing_enabled, durak.trump_suit, durak.trump_indicator_code,
                   durak.loser_user_id, durak.draw
            FROM recorded_durak_games durak JOIN recorded_games game ON game.id = durak.id
            JOIN recorded_game_players player ON player.game_id = game.id
            WHERE player.user_id = :userId ORDER BY game.ended_at ASC
            """, nativeQuery = true)
    List<RecordedDurakGame> findPastGamesByUserIdOldest(Long userId);
}
