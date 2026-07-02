package com.ultracards.server.controllers.games;

import com.ultracards.gateway.dto.games.GameTypeDTO;
import com.ultracards.gateway.dto.games.games.GameEntityDTO;
import com.ultracards.gateway.dto.games.games.ShortGameHistoryDTO;
import com.ultracards.gateway.dto.games.games.briskula.BriskulaGameHistoryDTO;
import com.ultracards.server.entity.UserEntity;
import com.ultracards.server.entity.games.briskula.BriskulaGameEntity;
import com.ultracards.server.service.games.briskula.BriskulaGameHistoryService;
import com.ultracards.server.service.games.briskula.BriskulaGameService;
import com.ultracards.server.service.games.GameManager;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/games")
@RequiredArgsConstructor
public class GameController {

    private final GameManager gameManager;
    private final BriskulaGameService briskulaGameService;
    private final BriskulaGameHistoryService briskulaGameHistoryService;

    @GetMapping("/lobby/{lobbyId}")
    @PreAuthorize("hasRole(T(com.ultracards.server.enums.UserRole).USER.name())")
    public ResponseEntity<GameEntityDTO> getGameByLobby(
            @PathVariable @NotBlank String lobbyId
    ) {
        var game = gameManager.getGameByLobbyId(UUID.fromString(lobbyId));
        if (game == null) return ResponseEntity.notFound().build();
        if (game.getGameType().equals(GameTypeDTO.Briskula))
            return ResponseEntity.ok(((BriskulaGameEntity) game).createGameDTO());
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
        return ResponseEntity.ok(briskulaGameHistoryService.getPastGames(user, offset, result, timeSort));
    }

    @GetMapping("/history/{gameId}")
    @PreAuthorize("hasRole(T(com.ultracards.server.enums.UserRole).USER.name())")
    public ResponseEntity<BriskulaGameHistoryDTO> getGameHistory(
            @PathVariable String gameId
    ) {
        var history = briskulaGameHistoryService.getGameHistory(UUID.fromString(gameId));
        if (history == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(history);
    }
}
