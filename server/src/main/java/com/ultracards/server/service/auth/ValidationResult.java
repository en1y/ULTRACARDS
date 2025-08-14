package com.ultracards.server.service.auth;

import com.ultracards.server.entity.UserEntity;
import com.ultracards.server.entity.auth.TokenEntity;
import com.ultracards.server.enums.TokenValidationResultStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;

import static com.ultracards.server.enums.TokenValidationResultStatus.*;

@Getter
@AllArgsConstructor
public class ValidationResult {
    private final TokenValidationResultStatus status;
    private final UserEntity user;
    private final TokenEntity token;

    public static ValidationResult proceed(TokenEntity token) {
        return new ValidationResult(PROCEED, token.getUser(), token);
    }
    public static ValidationResult rotated(TokenEntity newToken) {
        return new ValidationResult(ROTATED, newToken.getUser(), newToken);
    }
    public static ValidationResult logout() {
        return new ValidationResult(LOGOUT, null, null);
    }
}
