package com.ultracards.ui.webui.controller;

import com.ultracards.gateway.dto.auth.ProfileDTO;
import com.ultracards.gateway.dto.updated.games.GamePlayerDTO;
import com.ultracards.gateway.dto.updated.games.GameTypeDTO;
import com.ultracards.gateway.dto.updated.games.games.briskula.BriskulaGameConfigDTO;
import com.ultracards.gateway.dto.updated.games.lobby.GameLobbyDTO;
import com.ultracards.gateway.service.AuthService;
import com.ultracards.gateway.service.ClientTokenHolder;
import com.ultracards.gateway.service.LobbyService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.converter.HttpMessageConversionException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Controller
@RequestMapping("/games")
@RequiredArgsConstructor
public class GamesController {

    private final AuthService authService;
    private final RestTemplate restTemplate;
    @Qualifier("serverUrl")
    private final String serverUrl;

    @Value("${app.ultracards.server.url}")
    private String rawServerUrl;

    @GetMapping
    public String games(
            @CookieValue(name = "refreshToken", required = false) String token,
            @RequestParam(name = "type", required = false) String type,
            @RequestParam(name = "max", required = false) Integer max,
            HttpServletResponse response,
            Model model
    ) {
        ClientTokenHolder tokenHolder = token != null && !token.isBlank() ? new ClientTokenHolder(token) : null;
        ProfileDTO profile = null;
        if (tokenHolder != null) {
            profile = authService.getProfile(tokenHolder);
            response.addCookie(createCookie(tokenHolder));
        }

        ProfileController.setBasicModelAttributes(model, profile != null ? profile.getUsername() : null);
        String wsUrl = rawServerUrl.replaceFirst("^http", "ws");
        if (!wsUrl.endsWith("/")) wsUrl += "/";
        wsUrl += "ws";
        model.addAttribute("serverWsUrl", wsUrl);

        final GameTypeDTO filterType = resolveGameType(type);
        final Integer filterMax = max;

        List<GameLobbyDTO> lobbies = List.of();
        if (tokenHolder != null) {
            try {
                lobbies = lobbyService(tokenHolder).getLobbies();
                response.addCookie(createCookie(tokenHolder));
            } catch (HttpClientErrorException | HttpMessageConversionException ex) {
                lobbies = List.of();
            }
        }

        var filtered = lobbies.stream()
                .filter(l -> filterType == null || filterType.equals(l.getGameType()))
                .filter(l -> filterMax == null || l.getMaxPlayers() == filterMax)
                .sorted(Comparator.comparing(GameLobbyDTO::getGameType)
                        .thenComparing(GameLobbyDTO::getName, Comparator.nullsLast(String::compareToIgnoreCase)))
                .toList();

        model.addAttribute("lobbies", filtered);
        model.addAttribute("selectedType", filterType != null ? filterType.name() : "");
        model.addAttribute("selectedMax", filterMax);
        model.addAttribute("currentUserId", profile != null ? profile.getId() : null);
        model.addAttribute("isAuthenticated", profile != null);
        return "games";
    }

    @PostMapping("/create")
    public String createLobby(
            @CookieValue(value = "refreshToken", required = false) String token,
            @RequestParam(value = "lobbyName", required = false) String lobbyName,
            @RequestParam(value = "gameType", required = false) String gameType,
            @RequestParam(value = "maxPlayers", required = false) Integer maxPlayers,
            @RequestParam(value = "configJson", required = false) String configPreset,
            HttpServletResponse response
    ) {
        if (token == null || token.isBlank()) {
            return "redirect:/";
        }
        var holder = new ClientTokenHolder(token);
        var profile = authService.getProfile(holder);
        if (profile == null) {
            return "redirect:/";
        }
        var lobbyDto = buildLobbyDto(lobbyName, gameType, maxPlayers, configPreset, profile);
        try {
            var lobby = lobbyService(holder).createLobby(lobbyDto);
            response.addCookie(createCookie(holder));
            return lobby != null ? "redirect:/game?lobbyId=" + lobby.getId() : "redirect:/games?error=create_failed";
        } catch (HttpClientErrorException ex) {
            response.addCookie(createCookie(holder));
            return "redirect:/games?error=create_failed";
        }
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
        try {
            var ok = lobbyService(holder).joinLobby(lobbyId);
            response.addCookie(createCookie(holder));
            return ok ? "redirect:/game?lobbyId=" + lobbyId : "redirect:/games?error=join_failed";
        } catch (HttpClientErrorException ex) {
            response.addCookie(createCookie(holder));
            return "redirect:/games?error=join_failed";
        }
    }

    private LobbyService lobbyService(ClientTokenHolder tokenHolder) {
        return new LobbyService(restTemplate, serverUrl, tokenHolder);
    }

    private GameLobbyDTO buildLobbyDto(String lobbyName,
                                       String gameType,
                                       Integer maxPlayers,
                                       String configPreset,
                                       ProfileDTO profile) {
        var resolvedType = resolveGameType(gameType);
        var host = new GamePlayerDTO(profile.getUsername(), profile.getId());
        var config = mapBriskulaConfig(configPreset, maxPlayers);
        int playersCount = config != null && config.getNumberOfPlayers() != null
                ? config.getNumberOfPlayers()
                : (maxPlayers != null ? maxPlayers : 2);
        int max = maxPlayers != null ? maxPlayers : playersCount;
        if (config == null) {
            config = new BriskulaGameConfigDTO(playersCount, 3, false);
        }
        return new GameLobbyDTO(
                UUID.randomUUID(),
                (lobbyName != null && !lobbyName.isBlank()) ? lobbyName : "Lobby",
                playersCount,
                max,
                new HashSet<>(Set.of(host)),
                host,
                resolvedType != null ? resolvedType : GameTypeDTO.Briskula,
                config
        );
    }

    private BriskulaGameConfigDTO mapBriskulaConfig(String preset, Integer maxPlayers) {
        if (preset == null || preset.isBlank()) {
            return null;
        }
        switch (preset.toUpperCase(Locale.ROOT)) {
            case "TWO_PLAYERS":
                return new BriskulaGameConfigDTO(2, 3, false);
            case "TWO_PLAYERS_FOUR_CARDS_IN_HAND_EACH":
                return new BriskulaGameConfigDTO(2, 4, false);
            case "THREE_PLAYERS":
                return new BriskulaGameConfigDTO(3, 3, false);
            case "FOUR_PLAYERS_NO_TEAMS":
                return new BriskulaGameConfigDTO(4, 3, false);
            case "FOUR_PLAYERS_WITH_TEAMS":
                return new BriskulaGameConfigDTO(4, 3, true);
            default:
                var players = maxPlayers != null ? maxPlayers : 4;
                return new BriskulaGameConfigDTO(players, 3, false);
        }
    }

    private GameTypeDTO resolveGameType(String raw) {
        if (raw == null || raw.isBlank()) return null;
        return Arrays.stream(GameTypeDTO.values())
                .filter(gt -> gt.name().equalsIgnoreCase(raw) || gt.toString().equalsIgnoreCase(raw))
                .findFirst()
                .orElse(null);
    }

    private Cookie createCookie(ClientTokenHolder tokenHolder) {
        var cookie = new Cookie("refreshToken", tokenHolder.getToken());
        cookie.setPath("/");
        cookie.setMaxAge(60*60*24);
        cookie.setHttpOnly(true);
        return cookie;
    }
}
