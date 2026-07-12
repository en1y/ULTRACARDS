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
        // TODO: Consider adding JOIN, LEAVE, KICKED events for faster client processing. Do not forget to change lobbyEventPublisher if adding new Enum types
        CREATED, UPDATED, DELETED, STARTED
    }
}
