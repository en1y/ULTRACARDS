package com.ultracards.ui.controllers;

import com.ultracards.server.entity.UserEntity;
import com.ultracards.server.entity.lobby.LobbyState;
import com.ultracards.server.service.auth.AuthService;
import com.ultracards.server.service.auth.TokenService;
import com.ultracards.server.service.games.GameService;
import com.ultracards.server.service.lobby.LobbyService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.Optional;

@Controller
@RequestMapping("/lobbies")
@RequiredArgsConstructor
public class LobbiesController {

    private final AuthService authService;
    private final TokenService tokenService;
    private final LobbyService lobbyService;
    private final GameService gameService;

    @GetMapping
    @PreAuthorize("hasRole(T(com.ultracards.server.enums.UserRole).USER.name())")
    public String getLobbiesView(
            @AuthenticationPrincipal UserEntity user,
            Model model
    ) {
        model.addAttribute("isAuthenticated", true);
        model.addAttribute("username", user.getUsername());

        var lobby = lobbyService.getLobbyByUser(user);

        if (lobby == null) {
            model.addAttribute("lobbies", lobbyService.getLobbies());
            return "ui/lobbies";
        }

        if (lobby.getLobbyState().equals(LobbyState.STARTED)) {
            return "redirect:/game";
        }

        model.addAttribute("lobby", lobby.createLobbyDTO());
        return "ui/lobby";
    }
}
