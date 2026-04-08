package com.ultracards.ui.controllers;

import com.ultracards.server.entity.UserEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@RequiredArgsConstructor
public class HomeController {

    @GetMapping
    public String home(
            @AuthenticationPrincipal UserEntity user,
            Model model
    ) {
        var isAuthenticated = user != null;
        model.addAttribute("isAuthenticated", isAuthenticated);
        if (isAuthenticated) {
            model.addAttribute("username", user.getUsername());
        }
        return "ui/home";
    }
}
