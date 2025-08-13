package com.ultracards.server.service.auth;

import com.ultracards.server.entity.UserEntity;
import com.ultracards.server.enums.TokenValidationResultStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;

import static com.ultracards.server.enums.TokenValidationResultStatus.*;

@Getter
@AllArgsConstructor
public class ValidationResult {
    private final TokenValidationResultStatus status;
    private final UserEntity user;
    private final String newToken;

    public static ValidationResult proceed(UserEntity user) {
        return new ValidationResult(PROCEED, user, null);
    }
    public static ValidationResult rotated(String newToken) {
        return new ValidationResult(ROTATED, null, newToken);
    }
    public static ValidationResult logout() {
        return new ValidationResult(LOGOUT, null, null);
    }
}
