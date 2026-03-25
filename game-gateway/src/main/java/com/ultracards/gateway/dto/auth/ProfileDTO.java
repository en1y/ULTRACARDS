package com.ultracards.gateway.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.*;

import java.util.List;
import java.util.Map;

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
    private List<String> roles;
    private Long id;
    private Integer gamesPlayed;
    private Integer gamesWon;
    // Contains an array with 2 elements per game type where the 1st element is how many games are played and the 2nd element is how many games the user won
    private Map<String, Integer[]> playedAndWonGames;
}
