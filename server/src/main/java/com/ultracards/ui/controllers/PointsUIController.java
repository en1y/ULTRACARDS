package com.ultracards.ui.controllers;

import com.ultracards.server.entity.UserEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@RequiredArgsConstructor
public class PointsUIController {
    @GetMapping("/points")
    @PreAuthorize("hasRole(T(com.ultracards.server.enums.UserRole).USER.name())")
    public String points(@AuthenticationPrincipal UserEntity user, Model model) {
        model.addAttribute("isAuthenticated", true);
        model.addAttribute("username", user.getUsername());
        return "ui/points";
    }
}
