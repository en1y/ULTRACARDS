package com.ultracards.server.service.admin;

import com.ultracards.gateway.dto.admin.AdminRecordedGameDTO;
import com.ultracards.gateway.dto.admin.AdminRecordedGamePatchDTO;
import com.ultracards.recorder.RecordedBriskulaGame;
import com.ultracards.recorder.RecordedGame;
import com.ultracards.recorder.RecordedTresetaGame;
import com.ultracards.server.entity.UserEntity;
import com.ultracards.server.repositories.games.RecordedGameRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdminGameRecordService {
    private final RecordedGameRepository repository;
    private final AdminAuditService auditService;

    @Transactional(readOnly = true)
    public AdminRecordedGameDTO get(UUID id) {
        return toDto(find(id));
    }

    @Transactional
    public AdminRecordedGameDTO patch(UserEntity actor, UUID id, AdminRecordedGamePatchDTO patch) {
        if (patch.reason() == null || patch.reason().isBlank())
            throw badRequest("A nonblank reason is required");
        var name = patch.name() == null ? null : patch.name().trim();
        if (name == null || name.isBlank() || name.length() > 100)
            throw badRequest("Game name must contain 1 to 100 characters");
        var game = find(id);
        if (patch.dryRun())
            return new AdminRecordedGameDTO(game.id(), type(game), name, game.ownerUserId(), game.createdAt(),
                    game.startedAt(), game.endedAt());
        var oldName = game.name();
        game.rename(name);
        repository.save(game);
        auditService.record(actor.getId(), "UPDATE_GAME_RECORD", "RECORDED_GAME", id.toString(), patch.reason(),
                "name changed from '" + oldName + "' to '" + name + "'", "SUCCESS");
        return toDto(game);
    }

    AdminRecordedGameDTO toDto(RecordedGame game) {
        return new AdminRecordedGameDTO(game.id(), type(game), game.name(), game.ownerUserId(), game.createdAt(),
                game.startedAt(), game.endedAt());
    }

    private RecordedGame find(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Recorded game not found"));
    }

    private String type(RecordedGame game) {
        if (game instanceof RecordedBriskulaGame) return "BRISKULA";
        if (game instanceof RecordedTresetaGame) return "TRESETA";
        return "UNKNOWN";
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
