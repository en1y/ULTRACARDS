package com.ultracards.gateway.dto.admin;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AdminPointEventDTO(
        UUID id,
        String name,
        String description,
        String descriptionHtml,
        Instant startsAt,
        Instant endsAt,
        List<String> gameTypes,
        long completionRewardPoints,
        boolean enabled,
        List<Achievement> achievements
) {
    public record Achievement(
            UUID id,
            String name,
            String description,
            String descriptionHtml,
            int gamesRequired,
            int winsRequired,
            int lossesRequired,
            int drawsRequired,
            long rewardPoints,
            List<String> gameTypes,
            List<String> gameModes,
            boolean hidden
    ) {
    }
}
