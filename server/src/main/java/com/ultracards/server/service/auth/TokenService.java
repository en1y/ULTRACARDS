package com.ultracards.server.service.auth;

import com.ultracards.gateway.dto.auth.TokenDTO;
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

    private TokenEntity createToken(UserEntity user) {
        try {
            var token = new TokenEntity();
            token.setUser(user);
            token.setToken(createTokenValue());
            token.setExpiresAt(Instant.now().plusSeconds(tokenDurationMinutes * 60));
            return tokenRepository.save(token);
        } catch (DataIntegrityViolationException e) {
            return createToken(user);
        }
    }

    public ValidationResult validateToken(TokenDTO token) {
        var tokenEntity = tokenRepository.findByToken(token.getToken())
                .orElseThrow( () -> new IllegalArgumentException("Invalid token") );

        if (tokenEntity.getExpiresAt().isAfter(Instant.now())) {
            return ValidationResult.proceed(tokenEntity);
        }
        if (tokenEntity.isActive()) {
            tokenEntity.setActive(false);
            tokenRepository.save(tokenEntity);
            return ValidationResult.rotated(createToken(tokenEntity.getUser()));
        }
        return ValidationResult.logout();
    }

    public TokenEntity getTokenByUser(UserEntity user) {
        var token = tokenRepository.findByUser(user);
        if (token.isPresent()) {
            return validateToken(new TokenDTO(token.get().getToken())).getToken();
        }
        return createToken(user);
    }

    public void deleteTokenIfExists(String token) {
        var tokenEntity = tokenRepository.findByToken(token).orElse(null);
        if (tokenEntity == null) return;

        tokenRepository.delete(
                tokenEntity
        );
    }

}
