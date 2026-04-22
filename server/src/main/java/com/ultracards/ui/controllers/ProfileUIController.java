package com.ultracards.ui.controllers;

import com.ultracards.server.entity.UserEntity;
import com.ultracards.server.service.auth.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/profile")
@RequiredArgsConstructor
public class ProfileUIController {

    private final AuthService authService;

    @GetMapping
    @PreAuthorize("hasRole(T(com.ultracards.server.enums.UserRole).USER.name())")
    public String getProfile(
            @AuthenticationPrincipal UserEntity user,
            Model model
    ) {
        var isAuthenticated = user != null;
        model.addAttribute("isAuthenticated", isAuthenticated);
        var profile = authService.getProfile(user);
        model.addAttribute("username", user.getUsername());
        model.addAttribute("profile", profile);
        return "ui/profile";
    }
}
