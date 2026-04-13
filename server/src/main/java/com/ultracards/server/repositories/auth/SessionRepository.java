package com.ultracards.server.repositories.auth;

import com.ultracards.server.entity.auth.TokenEntity;
import com.ultracards.server.entity.auth.UserSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SessionRepository extends JpaRepository<UserSession, UUID> {
    public Optional<UserSession> findByToken(TokenEntity token);
}
