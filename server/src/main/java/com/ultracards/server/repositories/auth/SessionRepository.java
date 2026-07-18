package com.ultracards.server.repositories.auth;

import com.ultracards.server.entity.auth.TokenEntity;
import com.ultracards.server.entity.auth.UserSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SessionRepository extends JpaRepository<UserSession, UUID> {
    Optional<UserSession> findByToken(TokenEntity token);
    List<UserSession> findAllByUserId(Long userId);
    boolean existsByUserIdAndLastSeenAtAfter(Long userId, Instant lastSeenAt);
    long countByLastSeenAtAfter(Instant lastSeenAt);
    void deleteAllByUserId(Long userId);

    @Query("select count(s) from UserSession s where s.token.active = true and s.token.expiresAt > :now")
    long countValid(Instant now);

    @Query("select count(distinct s.userId) from UserSession s where s.lastSeenAt > :after")
    long countOnlineUsers(Instant after);

    @Query(value = """
            select s from UserSession s
            where (:userId is null or s.userId = :userId)
              and (:valid is null
                    or (:valid = true and s.token.active = true and s.token.expiresAt > :now)
                    or (:valid = false and (s.token.active = false or s.token.expiresAt <= :now)))
            """, countQuery = """
            select count(s) from UserSession s
            where (:userId is null or s.userId = :userId)
              and (:valid is null
                    or (:valid = true and s.token.active = true and s.token.expiresAt > :now)
                    or (:valid = false and (s.token.active = false or s.token.expiresAt <= :now)))
            """)
    Page<UserSession> findAdminReport(@Param("userId") Long userId, @Param("valid") Boolean valid,
                                      @Param("now") Instant now, Pageable pageable);
}
