package com.ultracards.server.service.auth;

import com.ultracards.gateway.dto.auth.TokenDTO;
import com.ultracards.server.entity.UserEntity;
import com.ultracards.server.entity.auth.TokenEntity;
import com.ultracards.server.repositories.auth.TokenRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

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

    @Value("${app.token.rotated-token-reuse-seconds:30}")
    private Long rotatedTokenReuseSeconds;

    private String createTokenValue() {
        var bytes = new byte[32];
        RNG.nextBytes(bytes);
        return URL_ENCODER.encodeToString(bytes);
    }

    @Transactional
    public TokenEntity createToken(UserEntity user) {
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
                .orElseThrow( () -> new IllegalArgumentException("Invalid token"));
    }

    public TokenEntity getTokenForUpdate(String token) {
        return tokenRepository.findByTokenForUpdate(token)
                .orElseThrow(() -> new AccessDeniedException("Invalid token"));
    }

    public TokenEntity resolveReusableToken(TokenEntity token, Instant now) {
        if (token.isActive()) {
            return token;
        }

        var replacement = token.getReplacementToken();
        var reuseUntil = token.getReuseUntil();

        if (replacement == null || reuseUntil == null || now.isAfter(reuseUntil)) {
            throw new AccessDeniedException("Token is no longer reusable");
        }

        return replacement;
    }

    public TokenEntity rotateTokenIfExpired(TokenEntity token, Instant now) {
        if (!token.isActive()) {
            return resolveReusableToken(token, now);
        }

        if (token.getExpiresAt().isAfter(now)) {
            return token;
        }

        var replacement = createToken(token.getUser());
        token.setActive(false);
        token.setReplacementToken(replacement);
        token.setReuseUntil(now.plusSeconds(rotatedTokenReuseSeconds));
        tokenRepository.save(token);
        return replacement;
    }

    @Transactional
    public ValidationResult validateToken(TokenDTO token) {
        try {
            var tokenEntity = getTokenForUpdate(token.getToken());
            var resolvedToken = rotateTokenIfExpired(tokenEntity, Instant.now());
            if (tokenEntity.getId().equals(resolvedToken.getId())) {
                return ValidationResult.proceed(resolvedToken);
            }
            return ValidationResult.rotated(resolvedToken);
        } catch (AccessDeniedException ex) {
            return ValidationResult.logout();
        }
    }

    public TokenEntity rotateToken (String token) {
        return rotateToken(new TokenDTO(token));
    }

    public TokenEntity rotateToken (TokenDTO token) {
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

    @Transactional
    public void deleteTokenIfExists(String token) {
        var tokenEntity = tokenRepository.findByToken(token).orElse(null);
        if (tokenEntity == null) return;

        tokenRepository.delete(
                tokenEntity
        );
    }

    @Transactional
    public void deleteToken(TokenEntity token) {
        if (token == null) return;
        tokenRepository.delete(token);
    }

    public boolean tokenExists(String token) {
        return tokenRepository.findByToken(token).isPresent();
    }
}
