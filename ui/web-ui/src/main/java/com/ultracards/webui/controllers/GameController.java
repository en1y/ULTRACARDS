package com.ultracards.webui.controllers;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Controller for game-related pages.
 * Handles game selection, creation, joining, and gameplay.
 */
@Controller
@RequestMapping("/games")
public class GameController extends BaseController {

    /**
     * Handles GET requests to the game selection page.
     * Renders the game selection view.
     * Redirects unauthenticated users to the home page.
     *
     * @param model The model to add attributes to
     * @param request The HTTP request
     * @return The name of the view to render or a redirect
     */
    @GetMapping
    public String gameSelection(Model model, HttpServletRequest request) {
        // Check if user is authenticated
        HttpSession session = request.getSession(false);
        if (!isAuthenticated(session)) {
            return "redirect:/";
        }
        
        // No additional model attributes needed for the game selection page
        return "game-selection";
    }

    /**
     * Handles GET requests to the create game page.
     * Renders the create game view.
     * Redirects unauthenticated users to the home page.
     *
     * @param model The model to add attributes to
     * @param request The HTTP request
     * @return The name of the view to render or a redirect
     */
    @GetMapping("/create")
    public String createGame(Model model, HttpServletRequest request) {
        // Check if user is authenticated
        HttpSession session = request.getSession(false);
        if (!isAuthenticated(session)) {
            return "redirect:/";
        }
        
        // No additional model attributes needed for the create game page
        return "create-game";
    }

    /**
     * Handles POST requests to start a new game.
     * In a real application, this would create a new game in the database.
     * For this demo, it simulates game creation and redirects to the game page.
     * Redirects unauthenticated users to the home page.
     *
     * @param gameType The type of game to create (briskula, durak, poker, etc.)
     * @param maxPlayers The number of players in the game
     * @param cardsInHand The number of cards in each player's hand (only applicable for Briskula with 2 players)
     * @param gameName The name of the game (optional)
     * @param model The model to add attributes to
     * @param request The HTTP request
     * @return A redirect to the game page
     */
    @PostMapping("/start")
    public String startGame(
            @RequestParam("gameType") String gameType,
            @RequestParam("maxPlayers") int maxPlayers,
            @RequestParam(value = "cardsInHand", required = false) Integer cardsInHand,
            @RequestParam(value = "gameName", required = false) String gameName,
            Model model,
            HttpServletRequest request) {
        
        // Check if user is authenticated
        HttpSession session = request.getSession(false);
        if (!isAuthenticated(session)) {
            return "redirect:/";
        }
        
        // In a real app, this would create a new game in the database
        // For this demo, we'll just redirect to the game page
        
        // Validate game parameters based on game type
        if ("briskula".equals(gameType)) {
            // Validate Briskula-specific parameters
            if (maxPlayers < 2 || maxPlayers > 4) {
                addErrorMessage(model, "Briskula supports 2-4 players");
                return "create-game";
            }
            
            // Validate cards in hand (only applicable for 2 players)
            if (maxPlayers == 2) {
                if (cardsInHand == null) {
                    addErrorMessage(model, "Number of cards in hand is required for 2-player Briskula games");
                    return "create-game";
                }
                
                if (cardsInHand != 3 && cardsInHand != 4) {
                    addErrorMessage(model, "Briskula with 2 players supports 3 or 4 cards in hand");
                    return "create-game";
                }
            }
        }
        
        // Simulate game creation with a random game ID
        String gameId = "game-" + System.currentTimeMillis();
        
        // Redirect to the game page
        return "redirect:/games/play?gameId=" + gameId;
    }

    /**
     * Handles GET requests to the join game page.
     * Renders the join game view.
     * Redirects unauthenticated users to the home page.
     *
     * @param model The model to add attributes to
     * @param request The HTTP request
     * @return The name of the view to render or a redirect
     */
    @GetMapping("/join")
    public String joinGame(Model model, HttpServletRequest request) {
        // Check if user is authenticated
        HttpSession session = request.getSession(false);
        if (!isAuthenticated(session)) {
            return "redirect:/";
        }
        
        // In a real app, this would fetch available games from the database
        // For this demo, we'll use the sample games in the template
        return "join-game";
    }

    /**
     * Handles POST requests to join an existing game.
     * In a real application, this would add the user to the game in the database.
     * For this demo, it simulates joining a game and redirects to the game page.
     * Redirects unauthenticated users to the home page.
     *
     * @param gameId The ID of the game to join
     * @param model The model to add attributes to
     * @param request The HTTP request
     * @return A redirect to the game page
     */
    @PostMapping("/join")
    public String joinExistingGame(
            @RequestParam("gameId") String gameId,
            Model model,
            HttpServletRequest request) {
        
        // Check if user is authenticated
        HttpSession session = request.getSession(false);
        if (!isAuthenticated(session)) {
            return "redirect:/";
        }
        
        // In a real app, this would add the user to the game in the database
        // For this demo, we'll just redirect to the game page
        
        // Redirect to the game page
        return "redirect:/games/play?gameId=" + gameId;
    }

    /**
     * Handles GET requests to the game page.
     * Renders the game view.
     * Redirects unauthenticated users to the home page.
     *
     * @param gameId The ID of the game to display
     * @param model The model to add attributes to
     * @param request The HTTP request
     * @return The name of the view to render or a redirect
     */
    @GetMapping("/play")
    public String playGame(
            @RequestParam("gameId") String gameId,
            Model model,
            HttpServletRequest request) {
        
        // Check if user is authenticated
        HttpSession session = request.getSession(false);
        if (!isAuthenticated(session)) {
            return "redirect:/";
        }
        
        // In a real app, this would fetch the game data from the database
        // For this demo, we'll just use the sample game in the template
        
        model.addAttribute("gameId", gameId);
        
        return "game";
    }
}