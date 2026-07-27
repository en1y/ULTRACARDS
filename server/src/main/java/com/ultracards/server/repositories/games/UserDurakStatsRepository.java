package com.ultracards.server.repositories.games;

import com.ultracards.server.entity.UserEntity;
import com.ultracards.server.entity.games.gamestats.UserDurakStats;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserDurakStatsRepository extends JpaRepository<UserDurakStats, UUID> {
    Optional<UserDurakStats> findByUser(UserEntity user);
}
