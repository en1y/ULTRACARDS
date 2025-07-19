package com.ultracards.server.service;

import com.ultracards.server.entity.UserEntity;
import com.ultracards.server.entity.auth.RefreshTokenEntity;
import com.ultracards.server.repositories.UserRepository;
import com.ultracards.server.repositories.auth.RefreshTokenRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
public class RefreshTokenService {

    private final RefreshTokenRepository tokenRepository;
    @Value("${app.refresh-token.duration-days}")
    private long refreshTokenDurationDays;

    public RefreshTokenService(RefreshTokenRepository tokenRepository, UserRepository userRepository) {
        this.tokenRepository = tokenRepository;
    }

    public RefreshTokenEntity createRefreshToken(UserEntity user) {
        deleteByUser(user);
        var token = new RefreshTokenEntity(
                user,
                UUID.randomUUID().toString(),
                Instant.now().plus(Duration.ofDays(refreshTokenDurationDays)));
        return tokenRepository.save(token);
    }

    public Optional<RefreshTokenEntity> findByToken(String token) {
        return tokenRepository.findByToken(token);
    }

    public boolean isExpired(RefreshTokenEntity token) {
        return token.getExpiryDate().isBefore(Instant.now());
    }

    public void deleteRefreshToken(RefreshTokenEntity token) {
        tokenRepository.delete(token);
    }

    public void deleteByUser(UserEntity user) {
        tokenRepository.deleteByUser(user);
    }

}
