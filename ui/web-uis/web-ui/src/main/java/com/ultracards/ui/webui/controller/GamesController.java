package com.ultracards.ui.webui.controller;

import com.ultracards.gateway.dto.games.LobbyCreateRequest;
import com.ultracards.gateway.dto.games.LobbyDTO;
import com.ultracards.gateway.dto.auth.ProfileDTO;
import com.ultracards.gateway.service.AuthService;
import com.ultracards.gateway.service.ClientTokenHolder;
import com.ultracards.gateway.service.GamesService;
import org.springframework.beans.factory.annotation.Value;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Controller
@RequestMapping("/games")
@RequiredArgsConstructor
public class GamesController {

    private final GamesService gamesService;
    private final AuthService authService;
    @Value("${app.ultracards.server.url}")
    private String serverUrl;

    @GetMapping
    public String games(
            @CookieValue(name = "refreshToken", required = false) String token,
            @RequestParam(name = "type", required = false) String type,
            @RequestParam(name = "max", required = false) Integer max,
            HttpServletResponse response,
            Model model
    ) {
        ClientTokenHolder tokenHolder = token != null ? new ClientTokenHolder(token) : null;
        ProfileDTO profile = null;
        if (tokenHolder != null) {
            profile = authService.getProfile(tokenHolder);
            response.addCookie(createCookie(tokenHolder));
        }

        ProfileController.setBasicModelAttributes(model, profile != null ? profile.getUsername() : null);
        // Provide WS URL for client (convert http(s) -> ws(s))
        String wsUrl = serverUrl.replaceFirst("^http", "ws");
        if (!wsUrl.endsWith("/")) wsUrl += "/";
        wsUrl += "ws";
        model.addAttribute("serverWsUrl", wsUrl);

        // Default to showing Briskula lobbies first if no type provided
        final String filterType = (type == null || type.isBlank()) ? "BRISKULA" : type;
        final Integer filterMax = max;

        List<LobbyDTO> lobbies = tokenHolder != null ? gamesService.listLobbies(tokenHolder) : List.of();
        if (tokenHolder != null) {
            response.addCookie(createCookie(tokenHolder));
        }

        var filtered = lobbies.stream()
                .filter(l -> filterType == null || filterType.isBlank() || filterType.equalsIgnoreCase(l.getGameType()))
                .filter(l -> filterMax == null || l.getMaxPlayers() == null || l.getMaxPlayers().equals(filterMax))
                .sorted(Comparator.comparing(LobbyDTO::getGameType).thenComparing(LobbyDTO::getLobbyName, Comparator.nullsLast(String::compareTo)))
                .toList();

        model.addAttribute("lobbies", filtered);
        model.addAttribute("selectedType", filterType == null ? "BRISKULA" : filterType);
        model.addAttribute("selectedMax", filterMax);
        model.addAttribute("currentUserId", profile != null ? profile.getId() : null);
        return "games";
    }

    @PostMapping("/create")
    public String createLobby(
            @CookieValue(value = "refreshToken", required = false) String token,
            @ModelAttribute LobbyCreateRequest request,
            HttpServletResponse response
    ) {
        if (token == null || token.isBlank()) {
            return "redirect:/";
        }
        var holder = new ClientTokenHolder(token);
        var lobby = gamesService.createLobby(holder, request);
        response.addCookie(createCookie(holder));
        return "redirect:/game?lobbyId=" + lobby.getId();
    }

    @PostMapping("/join")
    public String joinLobby(
            @CookieValue(value = "refreshToken", required = false) String token,
            @RequestParam("lobbyId") UUID lobbyId,
            HttpServletResponse response
    ) {
        if (token == null || token.isBlank()) {
            return "redirect:/";
        }
        var holder = new ClientTokenHolder(token);
        var lobby = gamesService.joinLobby(holder, lobbyId);
        response.addCookie(createCookie(holder));
        return "redirect:/game?lobbyId=" + lobby.getId();
    }

    private Cookie createCookie(ClientTokenHolder tokenHolder) {
        var cookie = new Cookie("refreshToken", tokenHolder.getToken());
        cookie.setPath("/");
        cookie.setMaxAge(60*60*24);
        cookie.setHttpOnly(true);
        return cookie;
    }
}
