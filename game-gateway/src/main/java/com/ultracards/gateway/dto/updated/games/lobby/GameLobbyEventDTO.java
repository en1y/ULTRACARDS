package com.ultracards.gateway.dto.updated.games.lobby;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class GameLobbyEventDTO {

    private GameLobbyEventType type;
    private GameLobbyDTO lobbyDto;

    public enum GameLobbyEventType {
        // TODO: Consider adding JOIN, LEAVE, KICKED events for faster client processing. Do not forget to change lobbyEventPublisher if adding new Enum types
        CREATED, UPDATED, DELETED, STARTED
    }
}
