package com.ultracards.webui.controllers;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Controller for the Welcome page.
 * Handles requests to the root path and renders the welcome view.
 */
@Controller
public class WelcomeController extends BaseController {

    /**
     * Handles GET requests to the root path.
     * Renders the welcome view.
     *
     * @param model The model to add attributes to
     * @return The name of the view to render
     */
    @GetMapping("/")
    public String welcome(Model model) {
        // No additional model attributes needed for the welcome page
        return "welcome";
    }
}