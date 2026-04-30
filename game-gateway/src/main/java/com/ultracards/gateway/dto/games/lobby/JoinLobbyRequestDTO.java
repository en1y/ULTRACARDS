package com.ultracards.gateway.dto.games.lobby;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.lang.annotation.*;
import java.util.UUID;

@ValidJoinLobbyRequest
public record JoinLobbyRequestDTO(
        @Size(min = 6, max = 6)
        @Pattern(regexp = "^[A-Za-z0-9]{6}$", message = "Code must be 6 characters A-Z and 0-9")
        String lobbyCode,
        UUID lobbyId
) {
        public boolean hasLobbyCode() {
            return lobbyCode != null;
        }
}

@Documented
@Constraint(validatedBy = JoinLobbyRequestValidator.class)
@Target({ ElementType.TYPE })
@Retention(RetentionPolicy.RUNTIME)
@interface ValidJoinLobbyRequest {

        String message() default "Either lobbyCode or lobbyId must be provided, but not both";
        Class<?>[] groups() default {};
        Class<? extends Payload>[] payload() default {};
}

class JoinLobbyRequestValidator
        implements ConstraintValidator<ValidJoinLobbyRequest, JoinLobbyRequestDTO> {

        @Override
        public boolean isValid(JoinLobbyRequestDTO dto, ConstraintValidatorContext context) {
                if (dto == null) return true;

                var hasCode = dto.lobbyCode() != null && !dto.lobbyCode().isBlank();
                var hasId = dto.lobbyId() != null;

                return hasCode ^ hasId;
        }
}