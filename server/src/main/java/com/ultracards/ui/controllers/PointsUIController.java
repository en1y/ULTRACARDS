package com.ultracards.ui.controllers;

import com.ultracards.gateway.dto.points.PointEventDTO;
import com.ultracards.server.entity.UserEntity;
import com.ultracards.server.service.points.PointsService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.time.Instant;
import java.util.List;

@Controller
@RequiredArgsConstructor
public class PointsUIController {
    private final PointsService pointsService;

    @GetMapping("/points")
    @PreAuthorize("hasRole(T(com.ultracards.server.enums.UserRole).USER.name())")
    public String points(@AuthenticationPrincipal UserEntity user, Model model) {
        model.addAttribute("isAuthenticated", true);
        model.addAttribute("username", user.getUsername());
        var account = pointsService.summary(user);
        model.addAttribute("pointsAccount", account);
        model.addAttribute("pointsBalance", account.balance());
        var now = Instant.now();
        var events = pointsService.events(user);
        var activeEvents = events.stream().filter(PointEventDTO::active).toList();
        // ponytail: no active events — fall back to the most recently finished one.
        // `events` puts upcoming events before history and sorts history newest-first,
        // so the first truly-ended match is the latest finish.
        var primary = !activeEvents.isEmpty() ? activeEvents
                : events.stream()
                        .filter(e -> e.endsAt() != null && !e.endsAt().isAfter(now))
                        .findFirst().map(List::of).orElseGet(List::of);
        var more = primary.isEmpty() ? events : events.stream().filter(e -> !primary.contains(e)).toList();
        model.addAttribute("pointsEvents", primary);
        model.addAttribute("pointsMoreEvents", more);
        model.addAttribute("pointsTransactions", pointsService.transactions(user, 0, 25));
        model.addAttribute("pointsSeries", pointsService.series(user.getId(), 3));
        model.addAttribute("pointsSettings", pointsService.settings());
        model.addAttribute("pointsNow", now);
        return "ui/points";
    }
}
