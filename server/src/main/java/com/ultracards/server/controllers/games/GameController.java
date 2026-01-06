package com.ultracards.server.controllers.games;

import com.ultracards.games.briskula.BriskulaPlayer;
import com.ultracards.gateway.dto.updated.games.GameTypeDTO;
import com.ultracards.gateway.dto.updated.games.games.GameCardDTO;
import com.ultracards.gateway.dto.updated.games.games.GameEntityDTO;
import com.ultracards.server.entity.UserEntity;
import com.ultracards.server.entity.games.PlayerEntity;
import com.ultracards.server.entity.games.briskula.BriskulaGameEntity;
import com.ultracards.server.entity.games.briskula.BriskulaPlayerEntity;
import com.ultracards.server.enums.games.GameType;
import com.ultracards.server.service.games.GameManager;
import com.ultracards.server.service.games.GameService;
import com.ultracards.templates.cards.AbstractCard;
import com.ultracards.templates.game.model.AbstractPlayer;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/games")
@RequiredArgsConstructor
public class GameController {

    private final GameManager gameManager;
    private final GameService gameService;

    @GetMapping("/{gameId}")
    @PreAuthorize("hasRole(T(com.ultracards.server.enums.UserRole).USER.name())")
    public ResponseEntity<GameEntityDTO> getGameInfo(
            @PathVariable String gameId
    ) {
        var game = gameManager.getGame(UUID.fromString(gameId));
        if (game != null && game.getGameType().equals(GameTypeDTO.Briskula))
            return ResponseEntity.ok(((BriskulaGameEntity)game).createGameDTO());
        return ResponseEntity.notFound().build();
    }
    
    @GetMapping
    @PreAuthorize("hasRole(T(com.ultracards.server.enums.UserRole).USER.name())")
    public ResponseEntity<List<GameCardDTO>> getPlayersCards(
            @AuthenticationPrincipal UserEntity user
    ) {
        var game = gameManager.getGame(user.getId());
        if (game != null) {
            for (var p: game.getGame().getPlayers()) {
                var player = (AbstractPlayer<?,?,?,?,?>) p;
                if (((PlayerEntity) p).getUser().getId().equals(user.getId())) {
                    var cards =  player.getHand().getCards();
                    var res = new ArrayList<GameCardDTO>();
                    for (var card: cards) {
                        res.add(GameCardDTO.createCardDTO(card));
                    }
                    return ResponseEntity.ok(res);
                }
            }
        }
        return ResponseEntity.notFound().build();
    }

    @PostMapping
    @PreAuthorize("hasRole(T(com.ultracards.server.enums.UserRole).USER.name())")
    public ResponseEntity<GameEntityDTO> playCard(
            @AuthenticationPrincipal UserEntity user,
            @Valid @RequestBody GameCardDTO card
    ) {
        var game = gameManager.getGame(user.getId());
        if (game != null) {
            gameService.playCard(user, card);
            if (game.getGameType().equals(GameTypeDTO.Briskula))
                return ResponseEntity.ok(((BriskulaGameEntity)game).createGameDTO());
            return ResponseEntity.ok().build();
        }
        return ResponseEntity.notFound().build();
    }

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
}
