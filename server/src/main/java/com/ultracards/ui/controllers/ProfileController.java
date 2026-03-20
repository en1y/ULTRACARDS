package com.ultracards.ui.controllers;

import com.ultracards.server.entity.UserEntity;
import com.ultracards.server.service.auth.AuthService;
import com.ultracards.server.service.auth.TokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/profile")
@RequiredArgsConstructor
public class ProfileController {

    private final AuthService authService;
    private final TokenService tokenService;

    @GetMapping
    private String getProfile(
            @AuthenticationPrincipal UserEntity user,
            Model model
    ) {
        var isAuthenticated = user != null;
        model.addAttribute("isAuthenticated", isAuthenticated);
        if (!isAuthenticated)
            return "redirect:/errors/403";
        var profile = authService.getProfile(tokenService.getTokenByUser(user));
        model.addAttribute("username", user.getUsername());
        model.addAttribute("profile", profile);
        return "ui/profile";
    }
}
