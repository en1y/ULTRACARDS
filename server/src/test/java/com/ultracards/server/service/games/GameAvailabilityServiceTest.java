package com.ultracards.server.service.games;

import com.ultracards.gateway.dto.games.GameTypeDTO;
import com.ultracards.gateway.dto.games.games.briskula.BriskulaGameConfigDTO;
import com.ultracards.server.entity.games.GameAvailability;
import com.ultracards.server.enums.games.GameType;
import com.ultracards.server.repositories.games.GameAvailabilityRepository;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GameAvailabilityServiceTest {
    private final GameAvailabilityRepository repository = mock(GameAvailabilityRepository.class);
    private final GameAvailabilityService service = new GameAvailabilityService(repository);

    @Test
    void defaultsEveryGameAndModeToEnabled() {
        when(repository.findAll()).thenReturn(List.of());

        var availability = service.list();

        assertThat(availability).anyMatch(value -> value.game().equals("BRISKULA") && value.mode() == null && value.enabled());
        assertThat(availability).anyMatch(value -> value.game().equals("TRESETA")
                && "FOUR_PLAYERS_WITH_TEAMS".equals(value.mode()) && value.enabled());
        assertThat(availability).anyMatch(value -> value.game().equals("DURAK")
                && "P4_D54_JOKERS_EVERYONE_PASS".equals(value.mode()) && value.enabled());
        assertThat(availability).noneMatch(value -> value.game().equals("DURAK")
                && "P5_D24_NO_JOKERS_EVERYONE_PASS".equals(value.mode()));
    }

    @Test
    void disablingADurakModeBlocksExactlyThatConfiguration() {
        when(repository.findByGameTypeAndMode(GameType.DURAK, "P2_D36_NO_JOKERS_EVERYONE_PASS"))
                .thenReturn(Optional.empty());

        var changed = service.setEnabled("durak", "p2_d36_no_jokers_everyone_pass", false);
        assertThat(changed.enabled()).isFalse();
        when(repository.findAll()).thenReturn(List.of(
                new GameAvailability(GameType.DURAK, "P2_D36_NO_JOKERS_EVERYONE_PASS", false)));

        var blocked = new com.ultracards.gateway.dto.games.games.durak.DurakGameConfigDTO(2, 36, false,
                com.ultracards.gateway.dto.games.games.durak.DurakThrowInPolicyDTO.EVERYONE, true, null);
        assertThatThrownBy(() -> service.requireEnabled(GameTypeDTO.Durak, blocked))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("DURAK / P2_D36_NO_JOKERS_EVERYONE_PASS");

        var allowed = new com.ultracards.gateway.dto.games.games.durak.DurakGameConfigDTO(2, 36, false,
                com.ultracards.gateway.dto.games.games.durak.DurakThrowInPolicyDTO.EVERYONE, false, null);
        service.requireEnabled(GameTypeDTO.Durak, allowed);
    }

    @Test
    void anUnknownDurakModeIsRejectedInsteadOfSilentlyIgnored() {
        assertThatThrownBy(() -> service.setEnabled("durak", "P9_D99_NO_JOKERS_EVERYONE_PASS", false))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Unknown DURAK mode");
    }

    @Test
    void persistsModeDisablingAndRejectsItsLobbyConfiguration() {
        when(repository.findByGameTypeAndMode(GameType.BRISKULA, "THREE_PLAYERS")).thenReturn(Optional.empty());

        var changed = service.setEnabled("briskula", "three_players", false);

        assertThat(changed.enabled()).isFalse();
        verify(repository).save(any(GameAvailability.class));
        when(repository.findAll()).thenReturn(List.of(new GameAvailability(GameType.BRISKULA, "THREE_PLAYERS", false)));

        assertThatThrownBy(() -> service.requireEnabled(GameTypeDTO.Briskula,
                new BriskulaGameConfigDTO(3, 3, false, null)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("BRISKULA / THREE_PLAYERS");
    }
}
