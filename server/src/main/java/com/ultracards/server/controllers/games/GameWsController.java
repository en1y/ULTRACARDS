package com.ultracards.server.controllers.games;

import com.ultracards.gateway.dto.games.games.GameCardDTO;
import com.ultracards.server.entity.games.PlayerEntity;
import com.ultracards.server.service.games.GameManager;
import com.ultracards.server.service.games.GameService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.UUID;

@Controller
@RequiredArgsConstructor
public class GameWsController {
    private final GameManager gameManager;
    private final GameService gameService;

    @MessageMapping("/game/play")
    public void playCard(Principal principal, @Payload @Valid GameCardDTO card) {
        var userId = UUID.fromString(principal.getName());
        var game = gameManager.getGame(userId);
        if (game == null) return;
        var user = game.getGame().getPlayers().stream()
                .filter(p -> ((PlayerEntity) p).getUser().getId().equals(userId))
                .map(p -> ((PlayerEntity) p).getUser())
                .findFirst().orElse(null);
        if (user == null) return;
        gameService.playCard(user, card, game);
    }
}
