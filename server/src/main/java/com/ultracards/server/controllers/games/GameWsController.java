package com.ultracards.server.controllers.games;

import com.ultracards.gateway.dto.games.games.GameCardDTO;
import com.ultracards.server.entity.games.PlayerEntity;
import com.ultracards.server.service.games.GameManager;
import com.ultracards.server.service.games.GameService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Controller
@RequiredArgsConstructor
public class GameWsController {
    private final GameManager gameManager;
    private final GameService gameService;

    @MessageMapping("/game/play")
    public void playCard(Principal principal, @Payload @Valid GameCardDTO card) {
        var userId = Long.valueOf(principal.getName());
        var game = gameManager.getGame(userId);
        if (game == null) return;
        var user = game.getGame().getPlayers().stream()
                .filter(p -> ((PlayerEntity) p).getUser().getId().equals(userId))
                .map(p -> ((PlayerEntity) p).getUser())
                .findFirst().orElse(null);
        if (user == null) return;
        gameService.playCard(user, card, game);
    }

    @MessageExceptionHandler(IllegalArgumentException.class)
    @SendToUser("/queue/game/errors")
    public GameMoveError handleInvalidMove(IllegalArgumentException ex) {
        return new GameMoveError(400, ex.getMessage());
    }

    public record GameMoveError(int status, String message) {
    }
}
