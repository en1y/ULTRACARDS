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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@Slf4j
@Controller
@RequestMapping("/errors")
@ControllerAdvice("com.ultracards.ui.controllers")
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
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String error404(
            @AuthenticationPrincipal UserEntity user,
            Model model
    ) {
        populateAuthModel(user, model);
        return "ui/errors/404";
    }

    @GetMapping("/500")
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public String error500(
            @AuthenticationPrincipal UserEntity user,
            Model model
    ) {
        populateAuthModel(user, model);
        return "ui/errors/500";
    }

    @ExceptionHandler(NoResourceFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String handleNoResourceFound(
            @AuthenticationPrincipal UserEntity user,
            Model model) {
        populateAuthModel(user, model);
        return "ui/errors/404";
    }

    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public String handleAccessDenied(
            AccessDeniedException exception,
            @AuthenticationPrincipal UserEntity user,
            Model model
    ) {
        log.warn("Unauthorized UI access: {}", exception.getMessage());
        populateAuthModel(user, model);
        return "ui/errors/401";
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public String handleServerError(
            Exception exception,
            @AuthenticationPrincipal UserEntity user,
            Model model
    ) {
        log.error("Unhandled UI error", exception);
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
