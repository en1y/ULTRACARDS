package com.ultracards.webui.controllers;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Controller for handling error pages.
 * Maps HTTP error codes to custom error templates.
 */
@Controller
public class ErrorController extends BaseController implements org.springframework.boot.web.servlet.error.ErrorController {

    @RequestMapping("/error")
    public String handleError(HttpServletRequest request, HttpServletResponse response, Model model) {
        var statusObj = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        var statusCode = statusObj != null ? Integer.parseInt(statusObj.toString()) : 0;

        var errorPage = "error/general";
        var errorMessage = "An unexpected error occurred";

        errorPage = switch (statusCode) {
            case 404 -> {
                errorMessage = "The page you are looking for could not be found";
                yield "error/404";
            }
            case 500 -> {
                errorMessage = "Internal server error occurred";
                yield "error/500";
            }
            case 403 -> {
                errorMessage = "Access to this resource is forbidden";
                yield "error/403";
            }
            case 418 -> {
                errorMessage = "Your request has been made too slow";
                clearCookies(request, response);
                yield "error/general";
            }
            default -> errorPage;
        };

        model.addAttribute("statusCode", statusCode);
        model.addAttribute("errorMessage", errorMessage);
        return errorPage;
    }

    private void clearCookies(HttpServletRequest request, HttpServletResponse response) {
        var cookies = request.getCookies();
        if (cookies != null) {
            for(var c: cookies) {
                var newCookie = new Cookie(c.getName(), null);
                newCookie.setMaxAge(0);
                newCookie.setPath("/");
                response.addCookie(newCookie);
            }
        }
    }
}