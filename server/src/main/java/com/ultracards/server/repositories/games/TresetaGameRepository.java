package com.ultracards.server.repositories.games;

import com.ultracards.recorder.RecordedTresetaGame;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface TresetaGameRepository extends JpaRepository<RecordedTresetaGame, UUID> {
    long countByEndedAtIsNull();
    long countByEndedAtIsNotNull();
    @Query(value = """
            SELECT game.*, treseta.game_config, treseta.teams_enabled
            FROM recorded_treseta_games treseta JOIN recorded_games game ON game.id = treseta.id
            JOIN recorded_game_players player ON player.game_id = game.id
            WHERE player.user_id = :userId ORDER BY game.ended_at DESC
            """, nativeQuery = true)
    List<RecordedTresetaGame> findPastGamesByUserIdLatest(Long userId);

    @Query(value = """
            SELECT game.*, treseta.game_config, treseta.teams_enabled
            FROM recorded_treseta_games treseta JOIN recorded_games game ON game.id = treseta.id
            JOIN recorded_game_players player ON player.game_id = game.id
            WHERE player.user_id = :userId ORDER BY game.ended_at ASC
            """, nativeQuery = true)
    List<RecordedTresetaGame> findPastGamesByUserIdOldest(Long userId);
}
