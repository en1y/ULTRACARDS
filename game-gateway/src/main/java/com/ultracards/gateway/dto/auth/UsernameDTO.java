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
    @Pattern(
            regexp = "^(?=.{1,64}$)[\\p{L}\\p{M}](?:[\\p{L}\\p{M}0-9 .,_\\-~']*[\\p{L}\\p{M}0-9])?$",
            message = "Username may contain Unicode letters (including Ukrainian, Russian, English, and Croatian), digits, spaces, dot, comma, underscore, dash, tilde, and apostrophe (1–64 chars)."
    )
    private String username;
}
