package com.ultracards.gateway.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ProfileDTO {
    @NotBlank @Email
    private String email;
    @NotBlank
    @Pattern(regexp = "^[A-Za-z0-9_.-]{1,64}$",
            message = "Username may contain letters, digits, dot, underscore, and dash (1–64 chars).")
    private String username;
    @NotBlank
    private String roles;
    private Long id;
    private Integer gamesPlayed;
    private Integer gamesWon;
    private Integer briskulaGamesPlayed;
    private Integer briskulaGamesWon;
    private Integer pokerGamesPlayed;
    private Integer pokerGamesWon;
    private Integer tresetaGamesPlayed;
    private Integer tresetaGamesWon;
    private Integer durakGamesPlayed;
    private Integer durakGamesWon;
}
