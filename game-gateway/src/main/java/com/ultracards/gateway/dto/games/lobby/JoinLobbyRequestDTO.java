package com.ultracards.gateway.dto.games.lobby;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record JoinLobbyRequestDTO(
        @NotBlank
        @Size(min = 6, max = 6)
        @Pattern(regexp = "^[A-Za-z0-9]{6}$", message = "Code must be 6 characters A-Z and 0-9")
        String lobbyCode
) {
}
