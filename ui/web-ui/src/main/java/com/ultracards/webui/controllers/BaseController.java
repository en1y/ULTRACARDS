package com.ultracards.webui.controllers;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Base controller class that provides common functionality for all controllers.
 * This follows the Spring MVC pattern with clear separation of concerns.
 */
public abstract class BaseController {
    
    /**
     * Adds common attributes to all models before rendering views.
     * This method is automatically called for all @RequestMapping methods in subclasses.
     * 
     * @param model The model to add attributes to
     * @param request The current HTTP request
     */
    @ModelAttribute
    public void addCommonAttributes(Model model, HttpServletRequest request) {
        // Add any common attributes that should be available in all views
        model.addAttribute("appName", "ULTRACARDS");
        model.addAttribute("currentYear", "2025");
        
        // Add a flag indicating whether the current request is for a games page
        String requestURI = request.getRequestURI();
        boolean isGamesPage = requestURI != null && requestURI.startsWith("/games");
        model.addAttribute("isGamesPage", isGamesPage);
        
        // Add authentication information
        HttpSession session = request.getSession(false);
        boolean isAuthenticated = isAuthenticated(session);
        model.addAttribute("isAuthenticated", isAuthenticated);
        
        if (isAuthenticated) {
            model.addAttribute("username", session.getAttribute("username"));
            model.addAttribute("email", session.getAttribute("email"));
            model.addAttribute("role", session.getAttribute("role"));
        }
    }
    
    /**
     * Checks if the user is authenticated.
     * 
     * @param session The HTTP session
     * @return true if the user is authenticated, false otherwise
     */
    protected boolean isAuthenticated(HttpSession session) {
        return session != null && Boolean.TRUE.equals(session.getAttribute("authenticated"));
    }
    
    /**
     * Helper method to add a success message to the model.
     * 
     * @param model The model to add the message to
     * @param message The success message
     */
    protected void addSuccessMessage(Model model, String message) {
        model.addAttribute("successMessage", message);
    }
    
    /**
     * Helper method to add an error message to the model.
     * 
     * @param model The model to add the message to
     * @param message The error message
     */
    protected void addErrorMessage(Model model, String message) {
        model.addAttribute("errorMessage", message);
    }
}