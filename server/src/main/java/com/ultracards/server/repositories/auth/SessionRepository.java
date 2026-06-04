package com.ultracards.server.repositories.auth;

import com.ultracards.server.entity.auth.TokenEntity;
import com.ultracards.server.entity.auth.UserSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SessionRepository extends JpaRepository<UserSession, UUID> {
    Optional<UserSession> findByToken(TokenEntity token);
    List<UserSession> findAllByUserId(Long userId);
    boolean existsByUserIdAndLastSeenAtAfter(Long userId, Instant lastSeenAt);
}
