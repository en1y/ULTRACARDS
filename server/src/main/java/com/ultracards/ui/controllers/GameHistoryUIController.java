package com.ultracards.ui.controllers;

import com.ultracards.gateway.dto.games.games.ShortGameHistoryDTO;
import com.ultracards.server.entity.UserEntity;
import com.ultracards.server.service.games.briskula.BriskulaGameHistoryService;
import com.ultracards.server.service.games.durak.DurakGameHistoryService;
import com.ultracards.server.service.games.treseta.TresetaGameHistoryService;
import lombok.RequiredArgsConstructor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
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
    private final TresetaGameHistoryService tresetaGameHistoryService;
    private final DurakGameHistoryService durakGameHistoryService;

    @GetMapping
    @PreAuthorize("hasRole(T(com.ultracards.server.enums.UserRole).USER.name())")
    public String getHistory(
            @AuthenticationPrincipal UserEntity user,
            Model model
    ) {
        model.addAttribute("isAuthenticated", true);
        model.addAttribute("username", user.getUsername());
        model.addAttribute("currentUserId", user.getId());
        model.addAttribute("initialHistory", initialHistory(user));
        return "ui/history";
    }

    /**
     * The first paint must match what the "all games" filter fetches straight after,
     * otherwise a player whose latest games are Treseta or Durak sees the list rebuild.
     */
    private List<ShortGameHistoryDTO> initialHistory(UserEntity user) {
        var histories = new ArrayList<ShortGameHistoryDTO>();
        histories.addAll(briskulaGameHistoryService.getPastGames(user, "both", "latest"));
        histories.addAll(tresetaGameHistoryService.getPastGames(user, "both", "latest"));
        histories.addAll(durakGameHistoryService.getPastGames(user, "both", "latest"));
        histories.sort(Comparator.comparing(ShortGameHistoryDTO::getEndedAt,
                Comparator.nullsLast(Comparator.<java.time.Instant>naturalOrder())).reversed());
        return histories.stream().limit(20).toList();
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
        // The replay seats the viewer at the bottom, exactly where they sat in the game.
        model.addAttribute("currentUserId", user.getId());
        model.addAttribute("gameEntityId", gameEntityId);
        return "ui/game-history";
    }
}
