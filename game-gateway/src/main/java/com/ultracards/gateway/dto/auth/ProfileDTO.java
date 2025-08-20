package com.ultracards.gateway.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ProfileDTO {
    @NotBlank @Email
    private String email;
    @NotBlank
    private String username;
    private int gamesPlayed;
    private int gamesWon;
    private int briskulaGamesPlayed;
    private int briskulaGamesWon;
    private int pokerGamesPlayed;
    private int pokerGamesWon;
    private int tresetaGamesPlayed;
    private int tresetaGamesWon;
    private int durakGamesPlayed;
    private int durakGamesWon;
}
