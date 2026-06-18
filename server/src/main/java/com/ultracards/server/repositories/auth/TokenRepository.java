package com.ultracards.server.repositories.auth;

import com.ultracards.server.entity.UserEntity;
import com.ultracards.server.entity.auth.TokenEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;

public interface TokenRepository extends JpaRepository<TokenEntity, UUID> {
    Optional<TokenEntity> findByToken(String token);
    Optional<TokenEntity> findFirstByUserOrderByExpiresAtDesc(UserEntity user);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select t
            from TokenEntity t
            left join fetch t.replacementToken
            where t.token = :token
            """)
    Optional<TokenEntity> findByTokenForUpdate(@Param("token") String token);
}
