package com.ultracards.server.controllers.games;

import com.ultracards.gateway.dto.games.GameTypeDTO;
import com.ultracards.gateway.dto.games.games.GameEntityDTO;
import com.ultracards.gateway.dto.games.games.ShortGameHistoryDTO;
import com.ultracards.gateway.dto.games.games.GameCardDTO;
import com.ultracards.gateway.dto.games.games.GameSnapshotDTO;
import com.ultracards.gateway.dto.games.games.briskula.BriskulaGameEntityDTO;
import com.ultracards.gateway.dto.games.games.treseta.TresetaGameEntityDTO;
import com.ultracards.gateway.dto.games.games.briskula.BriskulaGameHistoryDTO;
import com.ultracards.gateway.dto.games.games.treseta.TresetaGameHistoryDTO;
import com.ultracards.server.entity.UserEntity;
import com.ultracards.server.entity.games.briskula.BriskulaGameEntity;
import com.ultracards.server.entity.games.briskula.BriskulaPlayerEntity;
import com.ultracards.server.entity.games.treseta.TresetaGameEntity;
import com.ultracards.server.entity.games.treseta.TresetaPlayerEntity;
import com.ultracards.server.service.games.briskula.BriskulaGameHistoryService;
import com.ultracards.server.service.games.treseta.TresetaGameHistoryService;
import com.ultracards.server.service.games.GameManager;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.UUID;

@RestController
@RequestMapping("/api/games")
@RequiredArgsConstructor
public class GameController {

    private final GameManager gameManager;
    private final BriskulaGameHistoryService briskulaGameHistoryService;
    private final TresetaGameHistoryService tresetaGameHistoryService;

    @GetMapping("/lobby/{lobbyId}")
    @PreAuthorize("hasRole(T(com.ultracards.server.enums.UserRole).USER.name())")
    public ResponseEntity<GameEntityDTO> getGameByLobby(
            @PathVariable @NotBlank String lobbyId
    ) {
        var game = gameManager.getGameByLobbyId(UUID.fromString(lobbyId));
        if (game == null) return ResponseEntity.notFound().build();
        if (game.getGameType().equals(GameTypeDTO.Briskula))
            return ResponseEntity.ok(((BriskulaGameEntity) game).createGameDTO());
        if (game.getGameType().equals(GameTypeDTO.Treseta))
            return ResponseEntity.ok(((TresetaGameEntity) game).createGameDTO());
        return ResponseEntity.ok().build();
    }

    // FIXME: remove the option of deleting a game
    @DeleteMapping
    @PreAuthorize("hasRole(T(com.ultracards.server.enums.UserRole).USER.name())")
    public ResponseEntity<Void> deleteGame(
            @AuthenticationPrincipal UserEntity user
    ) {
        var game = gameManager.getGame(user.getId());
        if (game.getOwner().equals(user)) {
            gameManager.deleteGame(game);
            return ResponseEntity.ok().build();
        }
        return ResponseEntity.status(HttpStatus.NO_CONTENT).body(null);
    }

    @GetMapping("/history")
    @PreAuthorize("hasRole(T(com.ultracards.server.enums.UserRole).USER.name())")
    public ResponseEntity<List<ShortGameHistoryDTO>> getPastGames(
            @AuthenticationPrincipal UserEntity user,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "both") String result,
            @RequestParam(defaultValue = "latest") String timeSort
    ) {
        var histories = new ArrayList<ShortGameHistoryDTO>();
        histories.addAll(briskulaGameHistoryService.getPastGames(user, result, timeSort));
        histories.addAll(tresetaGameHistoryService.getPastGames(user, result, timeSort));
        var comparator = Comparator.comparing(ShortGameHistoryDTO::getEndedAt,
                Comparator.nullsLast(Comparator.naturalOrder()));
        if (!"oldest".equalsIgnoreCase(timeSort) && !"asc".equalsIgnoreCase(timeSort)) comparator = comparator.reversed();
        histories.sort(comparator);
        return ResponseEntity.ok(histories.stream().skip(Math.max(0, offset)).limit(20).toList());
    }

    @GetMapping("/history/{gameId}")
    @PreAuthorize("hasRole(T(com.ultracards.server.enums.UserRole).USER.name())")
    public ResponseEntity<?> getGameHistory(
            @PathVariable String gameId
    ) {
        var history = briskulaGameHistoryService.getGameHistory(UUID.fromString(gameId));
        if (history != null) return ResponseEntity.ok(history);
        var tresetaHistory = tresetaGameHistoryService.getGameHistory(UUID.fromString(gameId));
        if (tresetaHistory != null) return ResponseEntity.ok(tresetaHistory);
        return ResponseEntity.notFound().build();
    }

    @GetMapping("/history/briskula/{gameId}")
    @PreAuthorize("hasRole(T(com.ultracards.server.enums.UserRole).USER.name())")
    public ResponseEntity<BriskulaGameHistoryDTO> getBriskulaGameHistory(@PathVariable String gameId) {
        var history = briskulaGameHistoryService.getGameHistory(UUID.fromString(gameId));
        return history == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(history);
    }

    @GetMapping("/history/treseta/{gameId}")
    @PreAuthorize("hasRole(T(com.ultracards.server.enums.UserRole).USER.name())")
    public ResponseEntity<TresetaGameHistoryDTO> getTresetaGameHistory(@PathVariable String gameId) {
        var history = tresetaGameHistoryService.getGameHistory(UUID.fromString(gameId));
        return history == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(history);
    }

    @GetMapping("/{gameId}/snapshot/briskula")
    @PreAuthorize("hasRole(T(com.ultracards.server.enums.UserRole).USER.name())")
    public ResponseEntity<GameSnapshotDTO<BriskulaGameEntityDTO>> getBriskulaSnapshot(
            @PathVariable UUID gameId, @AuthenticationPrincipal UserEntity user) {
        var raw = gameManager.getGame(gameId);
        if (!(raw instanceof BriskulaGameEntity game) || !game.getPlayers().contains(user))
            return ResponseEntity.notFound().build();
        for (var player : game.getGame().getPlayers())
            if (((BriskulaPlayerEntity) player).getUser().equals(user))
                return ResponseEntity.ok(new GameSnapshotDTO<>(game.createGameDTO(),
                        player.getHand().getCards().stream().map(GameCardDTO::createCardDTO).toList()));
        return ResponseEntity.notFound().build();
    }

    @GetMapping("/{gameId}/snapshot/treseta")
    @PreAuthorize("hasRole(T(com.ultracards.server.enums.UserRole).USER.name())")
    public ResponseEntity<GameSnapshotDTO<TresetaGameEntityDTO>> getTresetaSnapshot(
            @PathVariable UUID gameId, @AuthenticationPrincipal UserEntity user) {
        var raw = gameManager.getGame(gameId);
        if (!(raw instanceof TresetaGameEntity game) || !game.getPlayers().contains(user))
            return ResponseEntity.notFound().build();
        for (var player : game.getGame().getPlayers())
            if (((TresetaPlayerEntity) player).getUser().equals(user))
                return ResponseEntity.ok(new GameSnapshotDTO<>(game.createGameDTO(),
                        player.getHand().getCards().stream().map(GameCardDTO::createCardDTO).toList()));
        return ResponseEntity.notFound().build();
    }
}
