package com.ultracards.server.controllers.games;

import com.ultracards.gateway.dto.games.GameDTO;
import com.ultracards.gateway.dto.games.GameResultDTO;
import com.ultracards.server.enums.games.GameType;
import com.ultracards.server.service.games.GameService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/games")
@RequiredArgsConstructor
public class GameController {

    private final GameService gameService;

    @GetMapping("/{gameId}")
    @PreAuthorize("hasRole(T(com.ultracards.server.enums.UserRole).USER.name())")
    public ResponseEntity<GameDTO> getGame(@PathVariable UUID gameId) {
        var game = gameService.getGame(gameId);
        if (game == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(gameService.toDto(game));
    }

    @PostMapping("/finish")
    @PreAuthorize("hasRole(T(com.ultracards.server.enums.UserRole).USER.name())")
    public ResponseEntity<Void> finishGame(@RequestBody @Valid GameResultDTO res, @RequestParam("type") GameType type) {
        gameService.finishGame(res, type);
        return ResponseEntity.ok().build();
    }
}

