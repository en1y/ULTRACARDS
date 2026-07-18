package com.ultracards.server.repositories.games;

import com.ultracards.server.entity.games.GameAvailability;
import com.ultracards.server.enums.games.GameType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface GameAvailabilityRepository extends JpaRepository<GameAvailability, UUID> {
    Optional<GameAvailability> findByGameTypeAndMode(GameType gameType, String mode);
}
