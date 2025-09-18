package com.ultracards.server.repositories.games;

import com.ultracards.server.entity.games.GameEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface GameEntityRepository extends JpaRepository<GameEntity, UUID> {
}

