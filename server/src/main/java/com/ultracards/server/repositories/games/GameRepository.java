package com.ultracards.server.repositories.games;

import com.ultracards.server.entity.UserEntity;
import com.ultracards.server.entity.games.GameEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * Repository for accessing and manipulating GameEntity objects.
 */
public interface GameRepository extends JpaRepository<GameEntity, String> {
    
    /**
     * Find games by status.
     *
     * @param status the status to search for
     * @return a list of games with the specified status
     */
    List<GameEntity> findByStatus(String status);
    
    /**
     * Find games by game type.
     *
     * @param gameType the game type to search for
     * @return a list of games with the specified game type
     */
    List<GameEntity> findByGameType(String gameType);
    
    /**
     * Find games by player.
     *
     * @param player the player to search for
     * @return a list of games that include the specified player
     */
    @Query("SELECT g FROM GameEntity g JOIN g.players p WHERE p = :player")
    List<GameEntity> findByPlayer(@Param("player") UserEntity player);
    
    /**
     * Find games by game type and status.
     *
     * @param gameType the game type to search for
     * @param status the status to search for
     * @return a list of games with the specified game type and status
     */
    List<GameEntity> findByGameTypeAndStatus(String gameType, String status);
}