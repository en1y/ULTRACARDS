package com.ultracards.server.repositories.auth;

import com.ultracards.server.entity.UserEntity;
import com.ultracards.server.entity.auth.RefreshTokenEntity;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;

import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshTokenEntity, UUID> {

    Optional<RefreshTokenEntity> findByToken(String token);
    @Modifying
    @Transactional
    void deleteByUser(UserEntity user);

}
