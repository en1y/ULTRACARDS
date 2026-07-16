package com.ultracards.gateway.dto.games.lobby;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class GameLobbyEventDTO {

    private GameLobbyEventType type;
    private GameLobbyDTO lobbyDto;
    private UUID gameId;

    public GameLobbyEventDTO(GameLobbyEventType type, GameLobbyDTO lobbyDto) {
        this(type, lobbyDto, null);
    }

    public enum GameLobbyEventType {
        CREATED, UPDATED, DELETED, STARTED, KICKED
    }
}
