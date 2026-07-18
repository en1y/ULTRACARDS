package com.ultracards.server.controllers.admin;

import com.ultracards.server.entity.UserEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class AdminPageController {
    @GetMapping("/admin")
    @PreAuthorize("hasRole(T(com.ultracards.server.enums.UserRole).ADMIN.name())")
    public String admin(@AuthenticationPrincipal UserEntity user, Model model) {
        model.addAttribute("isAuthenticated", true);
        model.addAttribute("username", user.getUsername());
        return "ui/admin";
    }
}
