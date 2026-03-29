package com.ultracards.ui.controllers;

import com.ultracards.server.entity.UserEntity;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@Controller
@RequestMapping("/errors")
@ControllerAdvice
public class ErrorController {
    @GetMapping("/401")
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public String error401(
            @AuthenticationPrincipal UserEntity user,
            Model model
            ) {
        populateAuthModel(user, model);
        return "ui/errors/401";
    }

    @GetMapping("/404")
    public String error404(
            @AuthenticationPrincipal UserEntity user,
            Model model
    ) {
        populateAuthModel(user, model);
        return "ui/errors/404";
    }

    @ExceptionHandler(NoResourceFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String handleNoResourceFound(
            @AuthenticationPrincipal UserEntity user,
            Model model) {
        populateAuthModel(user, model);
        return "ui/errors/404";
    }

    private void populateAuthModel(UserEntity user, Model model) {
        var isAuthenticated = user != null;
        model.addAttribute("isAuthenticated", isAuthenticated);
        if (isAuthenticated) {
            model.addAttribute("username", user.getUsername());
        }
    }
}
