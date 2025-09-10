package com.ultracards.gateway.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@NoArgsConstructor
@Getter
@AllArgsConstructor
@Setter
public class UsernameDTO {
    @NotBlank
    @Pattern(regexp = "^[A-Za-z0-9_.-]{1,64}$",
            message = "Username may contain letters, digits, dot, underscore, and dash (1–64 chars).")
    private String username;
}
