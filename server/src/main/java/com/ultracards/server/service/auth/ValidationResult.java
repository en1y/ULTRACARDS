package com.ultracards.server.service.auth;

import com.ultracards.server.entity.UserEntity;
import com.ultracards.server.entity.auth.TokenEntity;
import com.ultracards.server.enums.TokenValidationResultStatus;

import static com.ultracards.server.enums.TokenValidationResultStatus.*;

public record ValidationResult(TokenValidationResultStatus status, UserEntity user, TokenEntity token) {
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
