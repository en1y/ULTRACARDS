package com.ultracards.gateway.dto.games;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class GameEventDTO {
    private UUID gameId;
    private String eventType; // STATE, PLAYER_JOINED, PLAYER_LEFT, ACTION, HEARTBEAT, FINISHED
    private String payloadJson; // JSON payload for the event
    private Instant createdAt;
}

