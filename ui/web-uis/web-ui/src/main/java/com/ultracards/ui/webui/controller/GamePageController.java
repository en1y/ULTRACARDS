package com.ultracards.ui.webui.controller;

import com.ultracards.gateway.dto.auth.ProfileDTO;
import com.ultracards.gateway.dto.games.LobbyCreateRequest;
import com.ultracards.gateway.dto.games.LobbyDTO;
import com.ultracards.gateway.dto.games.StartGameRequest;
import com.ultracards.gateway.service.AuthService;
import com.ultracards.gateway.service.ClientTokenHolder;
import com.ultracards.gateway.service.GamesService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.Objects;
import java.util.UUID;

@Controller
@RequestMapping("/game")
@RequiredArgsConstructor
public class GamePageController {
    private final GamesService gamesService;
    private final AuthService authService;
    @Value("${app.ultracards.server.url}")
    private String serverUrl;

    @GetMapping
    public String game(
            @CookieValue(value = "refreshToken", required = false) String token,
            @RequestParam(value = "lobbyId", required = false) UUID lobbyId,
            @RequestParam(value = "gameId", required = false) UUID gameId,
            @RequestParam(value = "error", required = false) String error,
            HttpServletResponse response,
            Model model
    ) {
        if (token == null || token.isBlank()) {
            ProfileController.setBasicModelAttributes(model, null);
            return "redirect:/";
        }
        var th = new ClientTokenHolder(token);
        var profile = authService.getProfile(th);
        response.addCookie(createCookie(th));
        ProfileController.setBasicModelAttributes(model, profile.getUsername());
        addServerWsUrl(model);

        if (lobbyId != null) {
            // Find lobby info from list
            var lobby = gamesService.listLobbies(th).stream().filter(l -> Objects.equals(l.getId(), lobbyId)).findFirst().orElse(null);
            if (lobby == null) return "redirect:/games";
            hydrateLobbyModel(model, profile, lobby);
            if (error != null) model.addAttribute("error", error);
            return "game";
        }

        if (gameId != null) {
            var game = gamesService.getGame(th, gameId);
            response.addCookie(createCookie(th));
            model.addAttribute("game", game);
            model.addAttribute("isBriskula", "BRISKULA".equalsIgnoreCase(game.getGameType()));
            model.addAttribute("currentUserId", profile.getId());
            addServerWsUrl(model);
            return "game";
        }

        return "redirect:/games";
    }

    @PostMapping("/settings")
    public String updateSettings(
            @CookieValue("refreshToken") String token,
            @RequestParam("lobbyId") UUID lobbyId,
            @RequestParam(value = "lobbyName", required = false) String lobbyName,
            @RequestParam(value = "maxPlayers", required = false) Integer maxPlayers,
            @RequestParam(value = "configJson", required = false) String configJson,
            HttpServletResponse response,
            Model model
    ) {
        var th = new ClientTokenHolder(token);
        var req = new LobbyCreateRequest();
        req.setLobbyName(lobbyName);
        req.setMaxPlayers(maxPlayers);
        req.setConfigJson(configJson);
        try {
            var updated = gamesService.updateLobbySettings(th, lobbyId, req);
            response.addCookie(createCookie(th));

            // If current user is not in players, redirect to lobby selection
            var profile = authService.getProfile(th);
            var stillIn = updated.getPlayerIds() != null && updated.getPlayerIds().contains(profile.getId());
            if (!stillIn) return "redirect:/games";

            // Use PRG pattern to ensure header state and cookies are fresh
            return "redirect:/game?lobbyId=" + lobbyId + "&saved=1";
        } catch (org.springframework.web.client.HttpClientErrorException ex) {
            // Stay in the lobby page and show an error instead of redirecting to index
            response.addCookie(createCookie(th));
            return "redirect:/game?lobbyId=" + lobbyId + "&error=settings_failed";
        }
    }

    @PostMapping("/start")
    public String startGame(
            @CookieValue("refreshToken") String token,
            @RequestParam("lobbyId") UUID lobbyId,
            @RequestParam(value = "configJson", required = false) String configJson,
            HttpServletResponse response
    ) {
        var th = new ClientTokenHolder(token);
        // Avoid start request entirely when lobby is not yet full.
        var lobby = gamesService.listLobbies(th).stream()
                .filter(l -> Objects.equals(l.getId(), lobbyId))
                .findFirst()
                .orElse(null);
        response.addCookie(createCookie(th));
        if (lobby == null) {
            return "redirect:/games";
        }
        int playerCount = lobby.getPlayers() != null ? lobby.getPlayers().size() :
                (lobby.getPlayerIds() != null ? lobby.getPlayerIds().size() : 0);
        Integer maxPlayers = lobby.getMaxPlayers();
        if (maxPlayers != null && playerCount < maxPlayers) {
            return "redirect:/game?lobbyId=" + lobbyId + "&error=not_enough_players";
        }
        try {
            var res = gamesService.startGame(th, new StartGameRequest(lobbyId, configJson));
            response.addCookie(createCookie(th));
            return "redirect:/game?gameId=" + res.getId();
        } catch (org.springframework.web.client.HttpClientErrorException ex) {
            // Most likely not enough players or forbidden
            response.addCookie(createCookie(th));
            return "redirect:/game?lobbyId=" + lobbyId + "&error=not_enough_players";
        }
    }

    @PostMapping("/kick")
    public String kickPlayer(
            @CookieValue("refreshToken") String token,
            @RequestParam("lobbyId") UUID lobbyId,
            @RequestParam("playerId") Long playerId,
            HttpServletResponse response
    ) {
        var th = new ClientTokenHolder(token);
        try {
            gamesService.kickPlayer(th, lobbyId, playerId);
            response.addCookie(createCookie(th));
            return "redirect:/game?lobbyId=" + lobbyId;
        } catch (org.springframework.web.client.HttpClientErrorException ex) {
            response.addCookie(createCookie(th));
            return "redirect:/game?lobbyId=" + lobbyId + "&error=kick_failed";
        }
    }

    @PostMapping("/disband")
    public String disbandLobby(
            @CookieValue("refreshToken") String token,
            @RequestParam("lobbyId") UUID lobbyId,
            HttpServletResponse response
    ) {
        var th = new ClientTokenHolder(token);
        try {
            gamesService.deleteLobby(th, lobbyId);
            response.addCookie(createCookie(th));
            return "redirect:/games";
        } catch (org.springframework.web.client.HttpClientErrorException ex) {
            response.addCookie(createCookie(th));
            return "redirect:/game?lobbyId=" + lobbyId + "&error=disband_failed";
        }
    }

    @PostMapping("/leave")
    public String leaveLobby(
            @CookieValue("refreshToken") String token,
            @RequestParam("lobbyId") UUID lobbyId,
            HttpServletResponse response
    ) {
        var th = new ClientTokenHolder(token);
        try {
            gamesService.leaveLobby(th, lobbyId);
            response.addCookie(createCookie(th));
            return "redirect:/games";
        } catch (org.springframework.web.client.HttpClientErrorException ex) {
            response.addCookie(createCookie(th));
            return "redirect:/game?lobbyId=" + lobbyId + "&error=leave_failed";
        }
    }

    private void hydrateLobbyModel(Model model, ProfileDTO profile, LobbyDTO lobby) {
        model.addAttribute("lobby", lobby);
        model.addAttribute("isOwner", lobby.getOwnerId() != null && lobby.getOwnerId() == profile.getId());
        model.addAttribute("currentUserId", profile.getId());
        int playerCount = lobby.getPlayers() != null ? lobby.getPlayers().size() :
                (lobby.getPlayerIds() != null ? lobby.getPlayerIds().size() : 0);
        Integer maxPlayers = lobby.getMaxPlayers();
        boolean lobbyFull = maxPlayers == null || playerCount >= maxPlayers;
        model.addAttribute("lobbyFull", lobbyFull);
    }

    private Cookie createCookie(ClientTokenHolder tokenHolder) {
        var cookie = new Cookie("refreshToken", tokenHolder.getToken());
        cookie.setPath("/");
        cookie.setMaxAge(60*60*24);
        cookie.setHttpOnly(true);
        return cookie;
    }

    private void addServerWsUrl(Model model) {
        if (serverUrl == null) return;
        String wsUrl = serverUrl.replaceFirst("^http", "ws");
        if (!wsUrl.endsWith("/")) wsUrl += "/";
        wsUrl += "ws";
        model.addAttribute("serverWsUrl", wsUrl);
    }
}
