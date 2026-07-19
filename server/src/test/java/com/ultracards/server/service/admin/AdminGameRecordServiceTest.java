package com.ultracards.server.service.admin;

import com.ultracards.gateway.dto.admin.AdminRecordedGamePatchDTO;
import com.ultracards.recorder.RecordedGame;
import com.ultracards.server.entity.UserEntity;
import com.ultracards.server.repositories.games.RecordedGameRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class AdminGameRecordServiceTest {
    private final RecordedGameRepository repository = mock(RecordedGameRepository.class);
    private final AdminAuditService auditService = mock(AdminAuditService.class);
    private final AdminGameRecordService service = new AdminGameRecordService(repository, auditService);

    @Test
    void dryRunReturnsTheProposedNameWithoutMutatingTheRecording() {
        var id = UUID.randomUUID();
        var game = game(id, "Old name");
        when(repository.findById(id)).thenReturn(Optional.of(game));

        var result = service.patch(actor(), id, new AdminRecordedGamePatchDTO("New name", "preview", true));

        assertThat(result.name()).isEqualTo("New name");
        verify(game, never()).rename(anyString());
        verify(repository, never()).save(any());
        verifyNoInteractions(auditService);
    }

    @Test
    void mutationCanOnlyRenameTheRecording() {
        var id = UUID.randomUUID();
        var game = game(id, "Old name");
        when(repository.findById(id)).thenReturn(Optional.of(game));
        doAnswer(invocation -> { when(game.name()).thenReturn(invocation.getArgument(0)); return null; })
                .when(game).rename(anyString());

        var result = service.patch(actor(), id, new AdminRecordedGamePatchDTO("New name", "fix display", false));

        assertThat(result.name()).isEqualTo("New name");
        verify(game).rename("New name");
        verify(repository).save(game);
        verify(auditService).record(eq(1L), eq("UPDATE_GAME_RECORD"), eq("RECORDED_GAME"),
                eq(id.toString()), eq("fix display"), anyString(), eq("SUCCESS"), any());
    }

    private RecordedGame game(UUID id, String name) {
        var game = mock(RecordedGame.class);
        when(game.id()).thenReturn(id);
        when(game.name()).thenReturn(name);
        when(game.ownerUserId()).thenReturn(2L);
        when(game.createdAt()).thenReturn(Instant.EPOCH);
        return game;
    }

    private UserEntity actor() {
        var actor = new UserEntity("admin@example.com", "Admin");
        actor.setId(1L);
        return actor;
    }
}
