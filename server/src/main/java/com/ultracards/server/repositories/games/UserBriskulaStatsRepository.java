package com.ultracards.server.repositories.games;

import com.ultracards.server.entity.UserEntity;
import com.ultracards.server.entity.games.gamestats.UserBriskulaStats;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserBriskulaStatsRepository extends JpaRepository<UserBriskulaStats, UUID> {
    Optional<UserBriskulaStats> findByUser(UserEntity user);
}
