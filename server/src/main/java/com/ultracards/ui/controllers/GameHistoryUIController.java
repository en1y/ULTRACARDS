package com.ultracards.ui.controllers;

import com.ultracards.server.entity.UserEntity;
import com.ultracards.server.service.games.BriskulaGameHistoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/history")
@RequiredArgsConstructor
public class GameHistoryUIController {
    private final BriskulaGameHistoryService briskulaGameHistoryService;

    @GetMapping
    @PreAuthorize("hasRole(T(com.ultracards.server.enums.UserRole).USER.name())")
    public String getHistory(
            @AuthenticationPrincipal UserEntity user,
            Model model
    ) {
        model.addAttribute("isAuthenticated", true);
        model.addAttribute("username", user.getUsername());
        model.addAttribute("currentUserId", user.getId());
        model.addAttribute("initialHistory", briskulaGameHistoryService.getPastGames(user, 0, "both", "latest"));
        return "ui/history";
    }

    @GetMapping("/{gameEntityId}")
    @PreAuthorize("hasRole(T(com.ultracards.server.enums.UserRole).USER.name())")
    public String getGameHistoryView(
            @AuthenticationPrincipal UserEntity user,
            @PathVariable String gameEntityId,
            Model model
    ) {
        model.addAttribute("isAuthenticated", true);
        model.addAttribute("username", user.getUsername());
        model.addAttribute("gameEntityId", gameEntityId);
        return "ui/game-history";
    }
}
