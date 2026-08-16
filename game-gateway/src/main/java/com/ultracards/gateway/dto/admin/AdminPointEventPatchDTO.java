package com.ultracards.gateway.dto.admin;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AdminPointEventPatchDTO(
        String name,
        String description,
        Instant startsAt,
        Instant endsAt,
        List<String> gameTypes,
        Long completionRewardPoints,
        Boolean enabled,
        List<Achievement> achievements,
        String reason
) {
    public record Achievement(
            UUID id,
            String name,
            String description,
            Integer gamesRequired,
            Integer winsRequired,
            Integer lossesRequired,
            Integer drawsRequired,
            Long rewardPoints,
            List<String> gameTypes,
            Boolean hidden
    ) {
    }
}
