package com.ultracards.ui.controllers;

import com.ultracards.gateway.dto.games.games.GameEntityDTO;
import com.ultracards.server.entity.UserEntity;
import com.ultracards.server.entity.games.GameEntity;
import com.ultracards.server.entity.games.briskula.BriskulaGameEntity;
import com.ultracards.server.service.games.GameService;
import com.ultracards.server.service.lobby.LobbyService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/game")
@RequiredArgsConstructor
public class GameUIController {

    private final GameService gameService;
    private final LobbyService lobbyService;

    @GetMapping
    @PreAuthorize("hasRole(T(com.ultracards.server.enums.UserRole).USER.name())")
    public String getGameView(
            @AuthenticationPrincipal UserEntity user,
            Model model
    ) {
        var currentLobby = lobbyService.getLobbyByUser(user);
        var currentGame = gameService.getGameByUser(user).orElse(null);
        if (currentGame == null) {
            return "redirect:/lobbies";
        }
        if (currentLobby == null || !currentGame.getLobbyId().equals(currentLobby.getId())) {
            return "redirect:/lobbies";
        }
        var gameDto = toGameDto(currentGame);
        if (gameDto == null) {
            return "redirect:/lobbies";
        }

        model.addAttribute("isAuthenticated", true);
        model.addAttribute("username", user.getUsername());
        model.addAttribute("currentUserId", user.getId());
        model.addAttribute("game", gameDto);
        return "ui/game";
    }

    private GameEntityDTO toGameDto(GameEntity<?> game) {
        if (game instanceof BriskulaGameEntity briskulaGame) {
            return briskulaGame.createGameDTO();
        }
        return null;
    }
}
