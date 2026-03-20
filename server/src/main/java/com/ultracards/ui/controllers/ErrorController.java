package com.ultracards.ui.controllers;

import com.ultracards.server.entity.UserEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/errors")
@RequiredArgsConstructor
public class ErrorController {
    @GetMapping("/401")
    public String error401(
            @AuthenticationPrincipal UserEntity user,
            Model model
            ) {
        var isAuthenticated = user != null;
        model.addAttribute("isAuthenticated", isAuthenticated);
        if (isAuthenticated)
            model.addAttribute("username", user.getUsername());
        return "ui/errors/401";
    }
}
