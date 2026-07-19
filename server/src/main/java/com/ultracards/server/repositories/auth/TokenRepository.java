package com.ultracards.server.repositories.auth;

import com.ultracards.server.entity.UserEntity;
import com.ultracards.server.entity.auth.TokenEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface TokenRepository extends JpaRepository<TokenEntity, UUID> {
    Optional<TokenEntity> findByToken(String token);
    Optional<TokenEntity> findFirstByUserOrderByExpiresAtDesc(UserEntity user);

    @Query(value = """
            SELECT *
            FROM tokens
            WHERE token = :token
            FOR UPDATE
            """, nativeQuery = true)
    Optional<TokenEntity> findByTokenForUpdate(@Param("token") String token);

    @Query(value = """
            select t from TokenEntity t
            where (:id is null or t.id = :id)
              and (:userId is null or t.user.id = :userId)
              and (:active is null or t.active = :active)
            """, countQuery = """
            select count(t) from TokenEntity t
            where (:id is null or t.id = :id)
              and (:userId is null or t.user.id = :userId)
              and (:active is null or t.active = :active)
            """)
    Page<TokenEntity> findAdminReport(@Param("id") UUID id, @Param("userId") Long userId,
                                      @Param("active") Boolean active, Pageable pageable);
}
