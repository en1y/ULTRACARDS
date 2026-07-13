package com.ultracards.server.repositories.games;

import com.ultracards.recorder.RecordedBriskulaGame;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface BriskulaGameRepository extends JpaRepository<RecordedBriskulaGame, UUID> {

    @Query(value = """
            SELECT game.*, briskula.game_config, briskula.teams_enabled, briskula.trump_suit, briskula.trump_value
            FROM recorded_briskula_games briskula JOIN recorded_games game ON game.id = briskula.id
            JOIN recorded_game_players player ON player.game_id = game.id
            WHERE player.user_id = :userId
            ORDER BY game.ended_at DESC
            """, nativeQuery = true)
    List<RecordedBriskulaGame> findPastGamesByUserIdLatest(Long userId);

    @Query(value = """
            SELECT game.*, briskula.game_config, briskula.teams_enabled, briskula.trump_suit, briskula.trump_value
            FROM recorded_briskula_games briskula JOIN recorded_games game ON game.id = briskula.id
            JOIN recorded_game_players player ON player.game_id = game.id
            WHERE player.user_id = :userId
            ORDER BY game.ended_at ASC
            """, nativeQuery = true)
    List<RecordedBriskulaGame> findPastGamesByUserIdOldest(Long userId);
}
