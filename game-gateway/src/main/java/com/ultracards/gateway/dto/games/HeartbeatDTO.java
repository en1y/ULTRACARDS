package com.ultracards.gateway.dto.games;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class HeartbeatDTO {
    @NotNull
    private UUID gameId;
    private Instant sentAt;
    // Optional: for lobby/game presence, the client can include its user id
    private Long userId;
}
