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
import java.util.*;

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
            return toDto(game, name);
        var oldName = game.name();
        game.rename(name);
        repository.save(game);
        auditService.record(actor.getId(), "UPDATE_GAME_RECORD", "RECORDED_GAME", id.toString(), patch.reason(),
                "name changed from '" + oldName + "' to '" + name + "'", "SUCCESS", Map.of("name", oldName));
        return toDto(game);
    }

    @Transactional
    public void delete(UserEntity actor, UUID id, String reason) {
        if (reason == null || reason.isBlank()) throw badRequest("A nonblank reason is required");
        var game = find(id);
        var summary = type(game) + " " + game.name();
        repository.delete(game);
        auditService.record(actor.getId(), "DELETE_GAME_RECORD", "RECORDED_GAME", id.toString(), reason,
                "deleted " + summary, "SUCCESS");
    }

    AdminRecordedGameDTO toDto(RecordedGame game) {
        return toDto(game, game.name());
    }

    private AdminRecordedGameDTO toDto(RecordedGame game, String name) {
        var players = game.players().stream().map(player -> player.name() + " (#" + player.id() + ")").toList();
        var scores = new LinkedHashMap<Long, Integer>();
        var names = new HashMap<Long, String>();
        for (var player : game.players()) { scores.put(player.id(), 0); names.put(player.id(), player.name()); }
        for (var round : game.rounds()) {
            var winner = round.winner();
            if (winner == null) continue;
            var points = Integer.parseInt(round.attributes().getOrDefault("points", "0"));
            scores.merge(winner.id(), points, Integer::sum);
        }
        var best = scores.values().stream().mapToInt(Integer::intValue).max().orElse(Integer.MIN_VALUE);
        var winners = game.endedAt() == null ? List.<String>of() : scores.entrySet().stream()
                .filter(entry -> entry.getValue() == best)
                .map(entry -> names.get(entry.getKey()) + " (#" + entry.getKey() + ")").toList();
        return new AdminRecordedGameDTO(game.id(), type(game), mode(game), name, game.ownerUserId(), game.createdAt(),
                game.startedAt(), game.endedAt(), players, winners, game.rounds().size());
    }

    private String mode(RecordedGame game) {
        if (game instanceof RecordedBriskulaGame briskula) return briskula.gameConfig();
        if (game instanceof RecordedTresetaGame treseta) return treseta.gameConfig();
        return null;
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
