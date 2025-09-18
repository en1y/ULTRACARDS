package com.ultracards.gateway.dto.games;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class LobbyEventDTO {
    private String eventType; // CREATED, UPDATED, DELETED, STARTED, etc.
    private LobbyDTO lobby;
    private java.util.UUID gameId;
}
