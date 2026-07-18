package com.ultracards.gateway.dto.admin;

import com.ultracards.gateway.dto.games.lobby.GameLobbyDTO;

import java.time.Instant;

public record AdminLobbyDTO(GameLobbyDTO lobby, String state, Instant createdAt) {
}
