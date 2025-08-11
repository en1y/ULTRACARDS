package com.ultracards.server.controllers;

import com.ultracards.gateway.dto.games.GameActionRequestDTO;
import com.ultracards.gateway.dto.games.GameCreationRequestDTO;
import com.ultracards.gateway.dto.games.GameResponseDTO;
import com.ultracards.gateway.dto.games.GameSummaryDTO;
import com.ultracards.server.repositories.games.GameRepository;
import com.ultracards.server.service.games.GameService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for managing games.
 */
@RestController
@RequestMapping("/api/games")
public class GameController {

    private final GameService gameService;
    private final GameRepository gameRepository;

    public GameController(GameService gameService, GameRepository gameRepository) {
        this.gameService = gameService;
        this.gameRepository = gameRepository;
    }

    /**
     * Create a new game.
     *
     * @param request the game creation request
     * @return the created game
     */
    @PostMapping
    public ResponseEntity<GameResponseDTO> createGame(@RequestBody GameCreationRequestDTO request) {
        try {
            GameResponseDTO game = gameService.createGame(request);
            return ResponseEntity.status(HttpStatus.CREATED).body(game);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Get a game by ID.
     *
     * @param gameId the game ID
     * @return the game
     */
    @GetMapping("/{gameId}")
    public ResponseEntity<GameResponseDTO> getGame(@PathVariable String gameId) {
        try {
            var game = gameService.getGame(gameId);
            return ResponseEntity.ok(game);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Update a game with an action.
     *
     * @param gameId the game ID
     * @param request the game action request
     * @return the updated game
     */
    @PutMapping("/{gameId}")
    public ResponseEntity<GameResponseDTO> updateGame(@PathVariable String gameId, @RequestBody GameActionRequestDTO request) {
        try {
            GameResponseDTO game = gameService.updateGame(gameId, request);
            return ResponseEntity.ok(game);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * List all games.
     *
     * @return a list of all games
     */
    @GetMapping
    public ResponseEntity<List<GameSummaryDTO>> listGames(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String gameType,
            @RequestParam(required = false) Long playerId) {
        try {
            List<GameSummaryDTO> games;
            
            if (status != null) {
                games = gameService.listGamesByStatus(status);
            } else if (gameType != null) {
                games = gameService.listGamesByType(gameType);
            } else if (playerId != null) {
                games = gameService.listGamesByPlayer(playerId);
            } else {
                games = gameService.listGames();
            }
            
            return ResponseEntity.ok(games);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @DeleteMapping("/{gameId}")
    public ResponseEntity<Void> deactivateGames(@PathVariable String gameId) {
        try {
            var game = gameRepository.findById(gameId).orElseThrow(() -> new IllegalArgumentException("Game not found with id: " + gameId));
            gameService.deactivateGamesByPlayer(game.getCreator().getId());
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}