package com.ultracards.server.repositories.games;

import com.ultracards.server.entity.UserEntity;
import com.ultracards.server.entity.games.gamestats.UserTresetaStats;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserTresetaStatsRepository extends JpaRepository<UserTresetaStats, UUID> {
    Optional<UserTresetaStats> findByUser(UserEntity user);
}
