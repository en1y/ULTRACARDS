package com.ultracards.server.repositories.games;

import com.ultracards.server.entity.games.briskula.BriskulaGameEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BriskulaGameRepository extends JpaRepository<BriskulaGameEntity, UUID> {
    @Query("select game from BriskulaGameEntity game where game.id = :id")
    Optional<BriskulaGameEntity> findHistoryById(UUID id);

    @Query(value = """
            SELECT game.*
            FROM briskula_games game
            JOIN briskula_game_players player ON player.briskula_game_id = game.id
            WHERE player.user_id = :userId
            ORDER BY game.ended_at DESC
            LIMIT 20 OFFSET :offset
            """, nativeQuery = true)
    List<BriskulaGameEntity> findPastGamesByUserIdLatest(Long userId, int offset);

    @Query(value = """
            SELECT game.*
            FROM briskula_games game
            JOIN briskula_game_players player ON player.briskula_game_id = game.id
            WHERE player.user_id = :userId
            ORDER BY game.ended_at ASC
            LIMIT 20 OFFSET :offset
            """, nativeQuery = true)
    List<BriskulaGameEntity> findPastGamesByUserIdOldest(Long userId, int offset);
}
