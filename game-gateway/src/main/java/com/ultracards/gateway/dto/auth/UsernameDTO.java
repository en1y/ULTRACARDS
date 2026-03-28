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
    @Pattern(regexp = "^\\p{L}[\\p{L}0-9_.-]{0,63}$",
            message = "Username may contain letters (including Cyrillic and Croatian), digits, dot, underscore, and dash (1–64 chars).")
    private String username;
}
