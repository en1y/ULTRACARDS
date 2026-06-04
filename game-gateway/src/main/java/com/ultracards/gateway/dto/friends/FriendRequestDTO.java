package com.ultracards.gateway.dto.friends;

import com.ultracards.gateway.dto.games.GamePlayerDTO;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class FriendRequestDTO {
    private UUID id;
    private GamePlayerDTO requester;
    private GamePlayerDTO recipient;
    private FriendRequestStatusDTO status;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant respondedAt;
}
