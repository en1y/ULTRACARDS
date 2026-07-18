package com.ultracards.server.service.admin;

import com.ultracards.gateway.dto.admin.AdminStatsPatchDTO;
import com.ultracards.games.briskula.BriskulaGameConfig;
import com.ultracards.server.entity.UserEntity;
import com.ultracards.server.entity.games.gamestats.GameStats;
import com.ultracards.server.entity.games.gamestats.UserBriskulaStats;
import com.ultracards.server.repositories.UserRepository;
import com.ultracards.server.repositories.games.RecordedGameRepository;
import com.ultracards.server.repositories.games.UserBriskulaStatsRepository;
import com.ultracards.server.repositories.games.UserGamesStatsRepository;
import com.ultracards.server.repositories.games.UserTresetaStatsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AdminStatsServiceTest {
    private final UserRepository users = mock(UserRepository.class);
    private final UserGamesStatsRepository overall = mock(UserGamesStatsRepository.class);
    private final UserBriskulaStatsRepository briskula = mock(UserBriskulaStatsRepository.class);
    private final UserTresetaStatsRepository treseta = mock(UserTresetaStatsRepository.class);
    private final RecordedGameRepository games = mock(RecordedGameRepository.class);
    private final AdminAuditService audit = mock(AdminAuditService.class);
    private AdminStatsService service;

    @BeforeEach
    void setUp() {
        service = new AdminStatsService(users, overall, briskula, treseta, games, audit);
        var user = new UserEntity("user@example.com", "user");
        user.setId(1L);
        when(users.findById(1L)).thenReturn(Optional.of(user));
        when(overall.findByUser(user)).thenReturn(Optional.empty());
        when(briskula.findByUser(user)).thenReturn(Optional.empty());
        when(treseta.findByUser(user)).thenReturn(Optional.empty());
        when(games.findCompletedByUserId(1L)).thenReturn(List.of());
    }

    @Test
    void rejectsOmittedCountersInsteadOfTreatingThemAsZero() {
        var patch = new AdminStatsPatchDTO(null, null, null, "correction", true);

        assertThatThrownBy(() -> service.patch(actor(), 1L, "BRISKULA", "TWO_PLAYERS", patch))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Provide played, wins, or lastPlayedAt");
    }

    @Test
    void dryRunRejectsAnInvalidModeJustLikeARealWrite() {
        var patch = new AdminStatsPatchDTO(0, 0, null, "correction", true);

        assertThatThrownBy(() -> service.patch(actor(), 1L, "BRISKULA", "NOT_A_MODE", patch))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Unknown Briskula mode");
    }

    @Test
    void warnsWhenAnOverrideAddsStatsWithoutRecordedHistory() {
        var patch = new AdminStatsPatchDTO(1, 1, null, "correction", true);

        var result = service.patch(actor(), 1L, "BRISKULA", "TWO_PLAYERS", patch);

        assertThat(result.warning()).isEqualTo("Override differs from completed recorded-game history");
    }

    @Test
    void preservesUnselectedPersistedStatsWhenChangingOnlyWins() {
        var stats = new UserBriskulaStats();
        var lastPlayed = Instant.parse("2026-07-01T12:00:00Z");
        stats.getConfigStats().put(BriskulaGameConfig.TWO_PLAYERS, new GameStats(5, 2, lastPlayed));
        when(briskula.findByUser(org.mockito.ArgumentMatchers.any())).thenReturn(Optional.of(stats));
        var patch = new AdminStatsPatchDTO(null, 3, null, "correction", true);

        var result = service.patch(actor(), 1L, "BRISKULA", "TWO_PLAYERS", patch);

        var line = result.after().briskulaModes().get("TWO_PLAYERS");
        assertThat(line.played()).isEqualTo(5);
        assertThat(line.wins()).isEqualTo(3);
        assertThat(line.lastPlayedAt()).isEqualTo(lastPlayed);
    }

    private UserEntity actor() {
        var actor = new UserEntity("admin@example.com", "admin");
        actor.setId(2L);
        return actor;
    }
}
