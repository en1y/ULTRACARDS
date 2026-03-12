package com.ultracards.server.repositories.games;

import com.ultracards.server.entity.UserEntity;
import com.ultracards.server.entity.games.UserGamesStats;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserGamesStatsRepository extends JpaRepository<UserGamesStats, UUID> {
    Optional<UserGamesStats> findByUser(UserEntity user);
}
