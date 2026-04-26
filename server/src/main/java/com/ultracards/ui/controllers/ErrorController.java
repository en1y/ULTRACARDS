package com.ultracards.ui.controllers;

import com.ultracards.server.entity.UserEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@Slf4j
@Controller
@ControllerAdvice(assignableTypes = {
        HomeController.class,
        LobbiesController.class,
        GameUIController.class,
        ProfileUIController.class
})
public class ErrorController {
    @GetMapping("/errors/401")
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    @ExceptionHandler(AccessDeniedException.class)
    public String error401(
            @AuthenticationPrincipal UserEntity user,
            Model model
            ) {
        populateAuthModel(user, model);
        return "ui/errors/401";
    }

    @GetMapping("/errors/404")
    @ResponseStatus(HttpStatus.NOT_FOUND)
    @ExceptionHandler(NoResourceFoundException.class)
    public String error404(
            @AuthenticationPrincipal UserEntity user,
            Model model
    ) {
        populateAuthModel(user, model);
        return "ui/errors/404";
    }

    @GetMapping("/errors/500")
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    @ExceptionHandler(Exception.class)
    public String error500(
            @AuthenticationPrincipal UserEntity user,
            Model model
    ) {
        populateAuthModel(user, model);
        return "ui/errors/500";
    }

    private void populateAuthModel(UserEntity user, Model model) {
        var isAuthenticated = user != null;
        model.addAttribute("isAuthenticated", isAuthenticated);
        if (isAuthenticated) {
            model.addAttribute("username", user.getUsername());
        }
    }
}
