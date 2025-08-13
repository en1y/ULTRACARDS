package com.ultracards.server.service.auth;

import com.ultracards.server.entity.UserEntity;
import com.ultracards.server.entity.auth.TokenEntity;
import com.ultracards.server.repositories.auth.TokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;

@Service
@RequiredArgsConstructor
public class TokenService {

    private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final SecureRandom RNG = new SecureRandom();

    private final TokenRepository tokenRepository;

    @Value("${app.token.duration-minutes:15}")
    private Long tokenDurationMinutes;

    private String createTokenValue() {
        var bytes = new byte[32];
        RNG.nextBytes(bytes);
        return URL_ENCODER.encodeToString(bytes);
    }

    public String createToken(UserEntity user) {
        try {
            var token = new TokenEntity();
            token.setUser(user);
            token.setToken(createTokenValue());
            token.setExpiresAt(Instant.now().plusSeconds(tokenDurationMinutes * 60));
            return tokenRepository.save(token).getToken();
        } catch (DataIntegrityViolationException e) {
            return createToken(user);
        }
    }

    public ValidationResult validateToken(String token) {
        var tokenEntity = tokenRepository.findByToken(token)
                .orElseThrow( () -> new IllegalArgumentException("Invalid token") );

        if (tokenEntity.getExpiresAt().isAfter(Instant.now())) {
            return ValidationResult.proceed(tokenEntity.getUser());
        }
        if (tokenEntity.isActive()) {
            tokenEntity.setActive(false);
            tokenRepository.save(tokenEntity);
            return ValidationResult.rotated(createToken(tokenEntity.getUser()));
        }
        return ValidationResult.logout();

    }

}
