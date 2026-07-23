package com.ultracards.server.service.leaderboard;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class LeaderboardServiceValidationTest {
    private LeaderboardService service;

    @BeforeEach
    void setUp() {
        service = new LeaderboardService(mock(NamedParameterJdbcTemplate.class));
    }

    @Test
    void rejectsUnknownFiltersBeforeQuerying() {
        assertThatThrownBy(() -> service.get("luck", null, null, 0, 25, null))
                .hasMessageContaining("Unknown leaderboard metric");
        assertThatThrownBy(() -> service.get("wins", "Briskula", "not-a-mode", 0, 25, null))
                .hasMessageContaining("Unknown Briskula mode");
        assertThatThrownBy(() -> service.get("wins", null, "TWO_PLAYERS", 0, 25, null))
                .hasMessageContaining("gameType is required");
    }

    @Test
    void rejectsUnsafePaginationBounds() {
        assertThatThrownBy(() -> service.get("wins", null, null, -1, 25, null))
                .hasMessageContaining("page must be at least 0");
        assertThatThrownBy(() -> service.get("wins", null, null, 0, 101, null))
                .hasMessageContaining("size must be between");
    }
}
