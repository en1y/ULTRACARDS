package com.ultracards.server.service.auth;

import com.ultracards.gateway.dto.auth.TokenDTO;
import com.ultracards.server.entity.UserEntity;
import com.ultracards.server.entity.auth.TokenEntity;
import com.ultracards.server.repositories.auth.TokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.nio.file.AccessDeniedException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;

import static com.ultracards.server.enums.TokenValidationResultStatus.*;

@Slf4j
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

    public TokenEntity getToken(String token) {
         return tokenRepository.findByToken(token)
                .orElseThrow( () -> new IllegalArgumentException("Invalid token") );
    }

    public ValidationResult validateToken(TokenDTO token) {
        var tokenEntity = getToken(token.getToken());

        if (tokenEntity.getExpiresAt().isAfter(Instant.now())) {
            return ValidationResult.proceed(tokenEntity);
        }

        var user = tokenEntity.getUser();
        var isTokenActive = tokenEntity.isActive();
        tokenRepository.delete(tokenEntity);

        if (isTokenActive) {
            return ValidationResult.rotated(createToken(user));
        }
        return ValidationResult.logout();
    }

    public TokenEntity rotateToken (String token) throws AccessDeniedException {
        return rotateToken(new TokenDTO(token));
    }

    public TokenEntity rotateToken (TokenDTO token) throws AccessDeniedException {
        var validatedToken = validateToken(token);
        var tokenValidationStatus = validatedToken.status();

        if (tokenValidationStatus.equals(LOGOUT)) {
            log.info("Token \"{}\" is invalid. Redirecting to logout.", token.getToken());
            throw new AccessDeniedException("Token is invalid. Redirecting to logout");
        }

        if (tokenValidationStatus.equals(ROTATED) ||
                tokenValidationStatus.equals(PROCEED)) {
            return validatedToken.token();
        }

        log.error("Token status \"{}\" is not supported. Redirecting to logout.", tokenValidationStatus);
        throw new UnsupportedOperationException("Invalid token status: " + tokenValidationStatus);
    }

    public TokenEntity getTokenByUser(UserEntity user) {
        var token = tokenRepository.findByUser(user);
        if (token.isPresent()) {
            return validateToken(new TokenDTO(token.get().getToken())).token();
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
