package com.ultracards.gateway.dto.leaderboard;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ultracards.gateway.dto.games.GameTypeDTO;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LeaderboardContractTest {
    @Test
    void typedLeaderboardPageRoundTripsThroughJackson() throws Exception {
        var page = new LeaderboardPageDTO(
                List.of(new LeaderboardEntryDTO(1, 7L, "player", 20, 15, 75.0, true)),
                0, 25, 1, 1, 1L, 10, LeaderboardMetricDTO.WIN_RATE,
                GameTypeDTO.Treseta, "TWO_PLAYERS", List.of("TWO_PLAYERS")
        );

        var mapper = new ObjectMapper();
        var copy = mapper.readValue(mapper.writeValueAsBytes(page), LeaderboardPageDTO.class);

        assertEquals(LeaderboardMetricDTO.WIN_RATE, copy.metric());
        assertEquals(GameTypeDTO.Treseta, copy.gameType());
        assertEquals("TWO_PLAYERS", copy.mode());
        assertEquals(75.0, copy.items().getFirst().winRate());
        assertTrue(copy.items().getFirst().currentUser());
    }
}
