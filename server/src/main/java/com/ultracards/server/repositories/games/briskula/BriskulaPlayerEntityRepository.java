package com.ultracards.server.repositories.games.briskula;

import com.ultracards.server.entity.UserEntity;
import com.ultracards.server.entity.games.briskula.BriskulaPlayerEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BriskulaPlayerEntityRepository extends JpaRepository<BriskulaPlayerEntity, Long> {
    List<BriskulaPlayerEntity> findByUser(UserEntity user);
}
