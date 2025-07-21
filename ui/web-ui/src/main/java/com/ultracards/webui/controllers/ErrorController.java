package com.ultracards.webui.controllers;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Controller for handling error pages.
 * Maps HTTP error codes to custom error templates.
 */
@Controller
public class ErrorController extends BaseController implements org.springframework.boot.web.servlet.error.ErrorController {

    /**
     * Handles all error requests and maps them to appropriate error pages.
     *
     * @param request The HTTP request
     * @param model The model to add attributes to
     * @return The name of the error view to render
     */
    @RequestMapping("/error")
    public String handleError(HttpServletRequest request, Model model) {
        Object status = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        String errorMessage = "An unexpected error occurred";
        String errorPage = "error/general";
        
        if (status != null) {
            int statusCode = Integer.parseInt(status.toString());
            model.addAttribute("statusCode", statusCode);
            
            // Set specific error messages and pages based on status code
            if (statusCode == HttpStatus.NOT_FOUND.value()) {
                errorMessage = "The page you are looking for could not be found";
                errorPage = "error/404";
            } else if (statusCode == HttpStatus.INTERNAL_SERVER_ERROR.value()) {
                errorMessage = "Internal server error occurred";
                errorPage = "error/500";
            } else if (statusCode == HttpStatus.FORBIDDEN.value()) {
                errorMessage = "Access to this resource is forbidden";
                errorPage = "error/403";
            }
        }
        
        model.addAttribute("errorMessage", errorMessage);
        return errorPage;
    }
}