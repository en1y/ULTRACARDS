package com.ultracards.gateway.dto.friends;

import com.ultracards.gateway.dto.games.GamePlayerDTO;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class FriendDTO {
    private UUID friendRelationId;
    private GamePlayerDTO user;
    private FriendRelationStatusDTO status;
    private UserPresenceStatusDTO presenceStatus;
    private int totalPlayedTogether;
    private List<FriendPlayCountDTO> playedTogetherByGameType;
    private Instant friendsSince;
    private Instant removedAt;
}
