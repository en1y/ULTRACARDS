package com.ultracards.ui.controllers;

import com.ultracards.server.entity.UserEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class LeaderboardUIController {
    @GetMapping("/leaderboards")
    public String leaderboards(@AuthenticationPrincipal UserEntity user, Model model) {
        model.addAttribute("isAuthenticated", user != null);
        if (user != null) model.addAttribute("username", user.getUsername());
        return "ui/leaderboards";
    }
}
