package com.ultracards.webui.controllers;

import com.ultracards.gateway.dto.games.GameAction;
import com.ultracards.gateway.dto.games.GameResponseDTO;
import com.ultracards.gateway.dto.games.GameSummaryDTO;
import com.ultracards.gateway.dto.games.GamePlayerDTO;
import com.ultracards.gateway.service.GameService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Controller for game-related pages.
 * Handles game selection, creation, joining, and gameplay.
 */
@Controller
@RequestMapping("/games")
public class GameController extends BaseController {

    private final GameService gameService;

    @Autowired
    public GameController(GameService gameService) {
        this.gameService = gameService;
    }
    
    /**
     * Helper method to get the JWT token from the session.
     * 
     * @param session The HTTP session
     * @return The JWT token, or null if not found
     */
    private String getTokenFromSession(HttpSession session) {
        return (String) session.getAttribute("token");
    }

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
        try {
            // Get token from session
            String token = getTokenFromSession(session);
            
            // Get list of available games from server
            List<GameSummaryDTO> availableGames = gameService.listGames(token);
            model.addAttribute("availableGames", availableGames);
        } catch (Exception e) {
            // Handle errors gracefully
            addErrorMessage(model, "Failed to load games: " + e.getMessage());
            model.addAttribute("availableGames", Collections.emptyList());
        }
        
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
            @RequestParam("gameName") String gameName,
            Model model,
            HttpServletRequest request) {
        
        // Check if user is authenticated
        var session = request.getSession(false);
        if (!isAuthenticated(session)) {
            return "redirect:/";
        }
        
        // Get user ID from session
        Long userId = (Long) session.getAttribute("userId");
        if (userId == null) {
            addErrorMessage(model, "User ID not found in session");
            return "create-game";
        }
        
        // Validate game name
        if (gameName == null || gameName.trim().isEmpty()) {
            addErrorMessage(model, "Game name is required");
            return "create-game";
        }
        
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
        
        try {
            // Create game options map
            var gameOptions = new HashMap<String, Object>();
            gameOptions.put("maxPlayers", maxPlayers);
            if (cardsInHand != null) {
                gameOptions.put("cardsInHand", cardsInHand);
            }
            
            // Get token from session
            var token = getTokenFromSession(session);
            
            // Create the game using the game service
            var game = gameService.createGame(
                    gameName,
                    gameType, 
                    Collections.singletonList(userId),
                    userId,
                    gameOptions,
                    token);
            
            // Redirect to the game lobby page
            return "redirect:/games/lobby?gameId=" + game.getGameId();
        } catch (Exception e) {
            // Handle errors gracefully
            addErrorMessage(model, "Failed to create game: " + e.getMessage());
            return "create-game";
        }
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
        
        try {
            // Get token from session
            String token = getTokenFromSession(session);
            
            // Get list of available games from server
            List<GameSummaryDTO> availableGames = gameService.listGamesByStatus("CREATED", token);
            model.addAttribute("availableGames", availableGames);
        } catch (Exception e) {
            // Handle errors gracefully
            addErrorMessage(model, "Failed to load games: " + e.getMessage());
            model.addAttribute("availableGames", Collections.emptyList());
        }
        
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
        var session = request.getSession(false);
        if (!isAuthenticated(session)) {
            return "redirect:/";
        }
        
        // Get user ID from session
        var userId = (Long) session.getAttribute("userId");
        if (userId == null) {
            addErrorMessage(model, "User ID not found in session");
            return "join-game";
        }
        
        try {
            // Get token from session
            var token = getTokenFromSession(session);
            
            // Get the game from the server
            var game = gameService.getGame(gameId, token);
            
            // Update the game with the join action
            game = gameService.updateGame(gameId, userId, GameAction.JOIN_GAME, token);
            
            // Redirect to the game lobby page
            return "redirect:/games/lobby?gameId=" + gameId;
        } catch (Exception e) {
            // Handle errors gracefully
            addErrorMessage(model, "Failed to join game: " + e.getMessage());
            
            // Try to reload available games
            try {
                // Get token from session
                String token = getTokenFromSession(session);
                List<GameSummaryDTO> availableGames = gameService.listGamesByStatus("CREATED", token);
                model.addAttribute("availableGames", availableGames);
            } catch (Exception ex) {
                model.addAttribute("availableGames", Collections.emptyList());
            }
            
            return "join-game";
        }
    }

    /**
     * Handles GET requests to the game lobby page.
     * Renders the game lobby view where players wait for the game to start.
     * Redirects unauthenticated users to the home page.
     *
     * @param gameId The ID of the game to display
     * @param model The model to add attributes to
     * @param request The HTTP request
     * @return The name of the view to render or a redirect
     */
    @GetMapping("/lobby")
    public String gameLobby(
            @RequestParam("gameId") String gameId,
            Model model,
            HttpServletRequest request) {
        
        // Check if user is authenticated
        var session = request.getSession(false);
        if (!isAuthenticated(session)) {
            return "redirect:/";
        }
        
        // Get user ID from session
        var userId = (Long) session.getAttribute("userId");
        if (userId == null) {
            addErrorMessage(model, "User ID not found in session");
            return "redirect:/games";
        }
        
        try {
            // Get token from session
            var token = getTokenFromSession(session);
            
            // Get the game from the server
            var game = gameService.getGame(gameId, token);
            
            // Check if game is already in progress, redirect to game page if so
            if ("IN_PROGRESS".equals(game.getStatus()) || "FINISHED".equals(game.getStatus())) {
                return "redirect:/games/play?gameId=" + gameId;
            }
            
            // Get game options from game state
            Map<String, Object> gameOptions = null;
            if (game.getGameState() != null && game.getGameState().containsKey("options")) {
                gameOptions = (Map<String, Object>) game.getGameState().get("options");
            }
            
            // Determine max players from game options
            int maxPlayers = 4; // Default
            if (gameOptions != null && gameOptions.containsKey("maxPlayers")) {
                maxPlayers = ((Number) gameOptions.get("maxPlayers")).intValue();
            }
            
            // Determine minimum players required to start the game
            int minPlayers = 2; // Default minimum for most card games
            
            // Determine if current user is the host (first player who created the game)
            var isHost = false;
            Long hostId = null;
            if (game.getPlayers() != null && !game.getPlayers().isEmpty()) {
                var firstPlayer = game.getPlayers().get(0);
                hostId = firstPlayer.getPlayerId();
                isHost = Objects.equals(userId, hostId);
            }
            
            // Generate a shareable link for the game
            var gameLink = request.getScheme() + "://" +
                              request.getServerName() + ":" + 
                              request.getServerPort() + 
                              "/games/join?gameId=" + gameId;
            
            // Add game data to the model
            addGameDataToTheModel(gameId, model, game);
            model.addAttribute("gameOptions", gameOptions);
            model.addAttribute("currentUserId", userId);
            model.addAttribute("isHost", isHost);
            model.addAttribute("hostId", hostId);
            model.addAttribute("maxPlayers", maxPlayers);
            model.addAttribute("minPlayers", minPlayers);
            model.addAttribute("gameLink", gameLink);
            
            return "game-lobby";
        } catch (Exception e) {
            // Handle errors gracefully
            addErrorMessage(model, "Failed to load game lobby: " + e.getMessage());
            return "redirect:/games";
        }
    }

    private void addGameDataToTheModel(@RequestParam("gameId") String gameId, Model model, GameResponseDTO game) {
        model.addAttribute("game", game);
        model.addAttribute("gameId", gameId);
        model.addAttribute("gameType", game.getGameType());
        model.addAttribute("gameStatus", game.getStatus());
        model.addAttribute("players", game.getPlayers());
        model.addAttribute("gameState", game.getGameState());
        model.addAttribute("gameName", game.getGameName());
    }

    /**
     * Handles POST requests to start a game from the lobby.
     * Updates the game status from "CREATED" to "IN_PROGRESS" and redirects to the game page.
     * Only the host can start the game, and only when enough players have joined.
     *
     * @param gameId The ID of the game to start
     * @param model The model to add attributes to
     * @param request The HTTP request
     * @param redirectAttributes Attributes to pass to the redirect target
     * @return A redirect to the game page or back to the lobby
     */
    @PostMapping("/start-game/{gameId}")
    public String startGameFromLobby(
            @PathVariable String gameId,
            Model model,
            HttpServletRequest request,
            RedirectAttributes redirectAttributes) {
        
        // Check if user is authenticated
        HttpSession session = request.getSession(false);
        if (!isAuthenticated(session)) {
            return "redirect:/";
        }
        
        // Get user ID from session
        Long userId = (Long) session.getAttribute("userId");
        if (userId == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "User ID not found in session");
            return "redirect:/games";
        }
        
        try {
            // Get token from session
            String token = getTokenFromSession(session);
            
            // Get the game from the server
            GameResponseDTO game = gameService.getGame(gameId, token);
            
            // Check if game is already in progress or finished
            if ("IN_PROGRESS".equals(game.getStatus()) || "FINISHED".equals(game.getStatus())) {
                return "redirect:/games/play?gameId=" + gameId;
            }
            
            // Check if the current user is the host (first player)
            boolean isHost = false;
            if (game.getPlayers() != null && !game.getPlayers().isEmpty()) {
                GamePlayerDTO firstPlayer = game.getPlayers().get(0);
                isHost = Objects.equals(userId, firstPlayer.getPlayerId());
            }
            
            if (!isHost) {
                redirectAttributes.addFlashAttribute("errorMessage", "Only the host can start the game");
                return "redirect:/games/lobby?gameId=" + gameId;
            }
            
            // Check if there are enough players
            int minPlayers = 2; // Default minimum
            if (game.getPlayers() == null || game.getPlayers().size() < minPlayers) {
                redirectAttributes.addFlashAttribute("errorMessage", "Need at least " + minPlayers + " players to start the game");
                return "redirect:/games/lobby?gameId=" + gameId;
            }
            
            // Update the game with the start game action
            // This should change the status to "IN_PROGRESS"
            game = gameService.updateGame(gameId, userId, GameAction.START_GAME, token);
            
            // Redirect to the game page
            return "redirect:/games/play?gameId=" + gameId;
        } catch (Exception e) {
            // Handle errors gracefully
            redirectAttributes.addFlashAttribute("errorMessage", "Failed to start game: " + e.getMessage());
            return "redirect:/games/lobby?gameId=" + gameId;
        }
    }
    
    /**
     * Handles POST requests to leave a game.
     * Removes the player from the game and redirects to the game selection page.
     *
     * @param gameId The ID of the game to leave
     * @param model The model to add attributes to
     * @param request The HTTP request
     * @param redirectAttributes Attributes to pass to the redirect target
     * @return A redirect to the game selection page
     */
    @PostMapping("/leave")
    public String leaveGame(
            @RequestParam("gameId") String gameId,
            Model model,
            HttpServletRequest request,
            RedirectAttributes redirectAttributes) {
        
        // Check if user is authenticated
        var session = request.getSession(false);
        if (!isAuthenticated(session)) {
            return "redirect:/";
        }
        
        // Get user ID from session
        Long userId = (Long) session.getAttribute("userId");
        if (userId == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "User ID not found in session");
            return "redirect:/games";
        }
        
        try {
            // Get token from session
            String token = getTokenFromSession(session);
            
            // Update the game with the leave game action
            gameService.updateGame(gameId, userId, GameAction.LEAVE_GAME, token);
            
            // Redirect to the game selection page
            redirectAttributes.addFlashAttribute("successMessage", "You have left the game");
            return "redirect:/games";
        } catch (Exception e) {
            // Handle errors gracefully
            redirectAttributes.addFlashAttribute("errorMessage", "Failed to leave game: " + e.getMessage());
            return "redirect:/games";
        }
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
        
        // Get user ID from session
        Long userId = (Long) session.getAttribute("userId");
        if (userId == null) {
            addErrorMessage(model, "User ID not found in session");
            return "redirect:/games";
        }
        
        try {
            // Get token from session
            String token = getTokenFromSession(session);

//            TODO: add adding the creator player

            // Get the game from the server
            GameResponseDTO game = gameService.getGame(gameId, token);
            
            // Add game data to the model
            addGameDataToTheModel(gameId, model, game);
            model.addAttribute("currentUserId", userId);
            
            // Find the current player in the game
            game.getPlayers().stream()
                .filter(player -> player.getPlayerId().equals(userId))
                .findFirst()
                .ifPresent(player -> model.addAttribute("currentPlayer", player));
            
            return "game";
        } catch (Exception e) {
            // Handle errors gracefully
            addErrorMessage(model, "Failed to load game: " + e.getMessage());
            return "redirect:/games";
        }
    }
}