package com.ultracards.ui.controllers;

import com.ultracards.server.entity.UserEntity;
import com.ultracards.server.entity.lobby.LobbyState;
import com.ultracards.server.service.chat.ChatService;
import com.ultracards.server.service.lobby.LobbyService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/lobbies")
@RequiredArgsConstructor
public class LobbiesController {

    private final ChatService chatService;
    private final LobbyService lobbyService;

    @GetMapping
    @PreAuthorize("hasRole(T(com.ultracards.server.enums.UserRole).USER.name())")
    public String getLobby(
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
        model.addAttribute("chat", chatService.getChat(lobby.getId()).toDto());
        return "ui/lobby";
    }
}
