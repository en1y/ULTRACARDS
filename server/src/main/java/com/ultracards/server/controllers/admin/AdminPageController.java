package com.ultracards.server.controllers.admin;

import com.ultracards.server.entity.UserEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.Set;

@Controller
public class AdminPageController {
    private static final Set<String> PAGES = Set.of("users", "lobbies", "games", "sessions", "availability", "audit", "notifications", "stats", "database");

    @GetMapping({"/admin", "/admin/{page}"})
    @PreAuthorize("hasRole(T(com.ultracards.server.enums.UserRole).ADMIN.name())")
    public String admin(@PathVariable(required = false) String page, @AuthenticationPrincipal UserEntity user, Model model) {
        if (page != null && !PAGES.contains(page)) return "redirect:/admin";
        model.addAttribute("isAuthenticated", true);
        model.addAttribute("username", user.getUsername());
        model.addAttribute("adminPage", page == null ? "dashboard" : page);
        return "ui/admin";
    }
}
