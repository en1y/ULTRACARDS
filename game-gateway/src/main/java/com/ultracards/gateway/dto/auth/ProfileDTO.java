package com.ultracards.gateway.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ProfileDTO {
    @NotBlank @Email
    private String email;
    @NotBlank
    @Pattern(
            regexp = "^(?=.{1,64}$)[\\p{L}\\p{M}](?:[\\p{L}\\p{M}0-9 .,_\\-~']*[\\p{L}\\p{M}0-9])?$",
            message = "Username may contain letters, digits, dot, underscore, and dash (1–64 chars).")
    private String username;
    private List<String> roles;
    private Long id;
    private Integer gamesPlayed;
    private Integer gamesWon;
    private UserGamesStatsDTO userGamesStats;
}
