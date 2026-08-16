package com.ultracards.ui.controllers;

import com.ultracards.server.entity.UserEntity;
import com.ultracards.server.service.points.PointsService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.time.Instant;

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
        model.addAttribute("pointsEvents", pointsService.events(user));
        model.addAttribute("pointsTransactions", pointsService.transactions(user, 0, 25));
        model.addAttribute("pointsSeries", pointsService.series(user.getId(), 3));
        model.addAttribute("pointsSettings", pointsService.settings());
        model.addAttribute("pointsNow", Instant.now());
        return "ui/points";
    }
}
