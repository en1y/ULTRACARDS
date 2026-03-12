package com.ultracards.ui.webui.controller;

import com.ultracards.gateway.dto.games.games.GameCardDTO;
import com.ultracards.gateway.dto.games.games.briskula.BriskulaGameConfigDTO;
import com.ultracards.gateway.dto.games.lobby.GameLobbyDTO;
import com.ultracards.gateway.service.AuthService;
import com.ultracards.gateway.service.ClientTokenHolder;
import com.ultracards.gateway.service.GameService;
import com.ultracards.gateway.service.LobbyService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageConversionException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Controller
@RequestMapping("/game")
@RequiredArgsConstructor
public class GamePageController {

    private final AuthService authService;
    private final RestTemplate restTemplate;
    @Qualifier("serverUrl")
    private final String serverUrl;

    @Value("${app.ultracards.server.url}")
    private String rawServerUrl;

    @GetMapping
    public String gamePage(
            @CookieValue(name = "refreshToken", required = false) String token,
            @RequestParam(value = "lobbyId", required = false) UUID lobbyId,
            @RequestParam(value = "gameId", required = false) UUID gameId,
            HttpServletResponse response,
            Model model
    ) {
        if (token == null || token.isBlank()) {
            return "redirect:/";
        }
        var tokenHolder = new ClientTokenHolder(token);
        var profile = authService.getProfile(tokenHolder);
        if (profile == null) {
            return "redirect:/";
        }
        response.addCookie(createCookie(tokenHolder));

        ProfileController.setBasicModelAttributes(model, profile.getUsername());
        model.addAttribute("serverWsUrl", serverWsUrl());
        model.addAttribute("serverApiUrl", rawServerUrl.endsWith("/") ? rawServerUrl.substring(0, rawServerUrl.length() - 1) : rawServerUrl);
        model.addAttribute("currentUserId", profile.getId());

        GameLobbyDTO lobby = null;
        if (lobbyId != null && gameId == null) {
            lobby = findLobby(lobbyId, tokenHolder);
            if (lobby == null) {
                return "redirect:/games?error=lobby_not_found";
            }
            var isOwner = lobby.getHost() != null && profile.getId().equals(lobby.getHost().getId());
            var lobbyFull = lobby.getMaxPlayers() != null
                    && lobby.getPlayers() != null
                    && lobby.getPlayers().size() >= lobby.getMaxPlayers();
            model.addAttribute("lobby", lobby);
            model.addAttribute("isOwner", isOwner);
            model.addAttribute("lobbyFull", lobbyFull);

            if (lobby.getGameConfig() instanceof BriskulaGameConfigDTO briskulaConfig) {
                model.addAttribute("briskulaConfig", briskulaConfig);
                model.addAttribute("briskulaPreset", resolveBriskulaPreset(briskulaConfig));
            }
        }

        if (gameId != null) {
            var game = gameService(tokenHolder).getGame(gameId);
            if (game != null) {
                model.addAttribute("game", game);
            }
            try {
                var playerCards = gameService(tokenHolder).getPlayersCards();
                model.addAttribute("playerCards", playerCards != null ? playerCards : List.of());
            } catch (HttpClientErrorException ignored) {
                model.addAttribute("playerCards", List.of());
            }
        }

        if (gameId != null) {
            return "game";
        }
        if (lobbyId != null) {
            return "lobby";
        }
        return "redirect:/games";
    }

    @PostMapping("/settings")
    public String updateLobbySettings(
            @CookieValue(name = "refreshToken", required = false) String token,
            @RequestParam("lobbyId") UUID lobbyId,
            @RequestParam(value = "lobbyName", required = false) String lobbyName,
            @RequestParam(value = "maxPlayers", required = false) Integer maxPlayers,
            @RequestParam(value = "configJson", required = false) String configPreset,
            HttpServletResponse response
    ) {
        if (token == null || token.isBlank()) {
            return "redirect:/";
        }
        var tokenHolder = new ClientTokenHolder(token);
        var lobby = findLobby(lobbyId, tokenHolder);
        if (lobby == null) {
            return "redirect:/games?error=lobby_not_found";
        }

        if (lobbyName != null && !lobbyName.isBlank()) {
            lobby.setName(lobbyName.trim());
        }
        var updatedMax = maxPlayers != null ? maxPlayers : lobby.getMaxPlayers();
        if (updatedMax != null) {
            lobby.setMaxPlayers(updatedMax);
            lobby.setMinPlayers(updatedMax);
        }

        if (configPreset != null && !configPreset.isBlank()) {
            lobby.setGameConfig(mapBriskulaConfig(configPreset, updatedMax));
        } else if (lobby.getGameConfig() instanceof BriskulaGameConfigDTO config && updatedMax != null) {
            config.setNumberOfPlayers(updatedMax);
            lobby.setGameConfig(config);
        }

        try {
            lobbyService(tokenHolder).updateLobby(lobby);
            response.addCookie(createCookie(tokenHolder));
            return "redirect:/game?lobbyId=" + lobbyId + "&saved=1";
        } catch (HttpClientErrorException | HttpMessageConversionException ex) {
            response.addCookie(createCookie(tokenHolder));
            return "redirect:/game?lobbyId=" + lobbyId + "&error=settings";
        }
    }

    @PostMapping("/start")
    public String startLobby(
            @CookieValue(name = "refreshToken", required = false) String token,
            @RequestParam("lobbyId") UUID lobbyId,
            HttpServletResponse response
    ) {
        if (token == null || token.isBlank()) {
            return "redirect:/";
        }
        var tokenHolder = new ClientTokenHolder(token);
        try {
            var ok = lobbyService(tokenHolder).startLobby();
            response.addCookie(createCookie(tokenHolder));
            return ok ? "redirect:/game?lobbyId=" + lobbyId : "redirect:/game?lobbyId=" + lobbyId + "&error=not_enough_players";
        } catch (HttpClientErrorException ex) {
            response.addCookie(createCookie(tokenHolder));
            return "redirect:/game?lobbyId=" + lobbyId + "&error=not_enough_players";
        }
    }

    @PostMapping("/leave")
    public String leaveLobby(
            @CookieValue(name = "refreshToken", required = false) String token,
            @RequestParam("lobbyId") UUID lobbyId,
            HttpServletResponse response
    ) {
        if (token == null || token.isBlank()) {
            return "redirect:/";
        }
        var tokenHolder = new ClientTokenHolder(token);
        lobbyService(tokenHolder).leaveLobby(lobbyId);
        response.addCookie(createCookie(tokenHolder));
        return "redirect:/games";
    }

    @PostMapping("/kick")
    public String kickPlayer(
            @CookieValue(name = "refreshToken", required = false) String token,
            @RequestParam("lobbyId") UUID lobbyId,
            @RequestParam("playerId") Long playerId,
            HttpServletResponse response
    ) {
        if (token == null || token.isBlank()) {
            return "redirect:/";
        }
        var tokenHolder = new ClientTokenHolder(token);
        lobbyService(tokenHolder).kickPlayer(playerId);
        response.addCookie(createCookie(tokenHolder));
        return "redirect:/game?lobbyId=" + lobbyId;
    }

    @PostMapping("/disband")
    public String deleteLobby(
            @CookieValue(name = "refreshToken", required = false) String token,
            HttpServletResponse response
    ) {
        if (token == null || token.isBlank()) {
            return "redirect:/";
        }
        var tokenHolder = new ClientTokenHolder(token);
        lobbyService(tokenHolder).deleteLobby();
        response.addCookie(createCookie(tokenHolder));
        return "redirect:/games";
    }

    @PostMapping("/play")
    public String playCard(
            @CookieValue(name = "refreshToken", required = false) String token,
            @RequestParam("gameId") UUID gameId,
            @RequestParam("cardType") String cardType,
            @RequestParam("card") String card,
            HttpServletResponse response
    ) {
        if (token == null || token.isBlank()) {
            return "redirect:/";
        }
        var tokenHolder = new ClientTokenHolder(token);
        try {
            var headers = authJsonHeaders(tokenHolder);
            var payload = java.util.Map.of("cardType", cardType, "card", card);
            var res = restTemplate.postForEntity(
                    serverUrl.endsWith("/") ? serverUrl + "api/games" : serverUrl + "/api/games",
                    new HttpEntity<>(payload, headers),
                    Object.class
            );
            updateTokenFromHeaders(tokenHolder, res.getHeaders());
            response.addCookie(createCookie(tokenHolder));
            return "redirect:/game?gameId=" + gameId;
        } catch (HttpClientErrorException ex) {
            response.addCookie(createCookie(tokenHolder));
            return "redirect:/game?gameId=" + gameId + "&error=play_failed";
        }
    }

    @GetMapping("/cards")
    @ResponseBody
    public ResponseEntity<List<GameCardDTO>> getPlayerCards(
            @CookieValue(name = "refreshToken", required = false) String token,
            HttpServletResponse response
    ) {
        if (token == null || token.isBlank()) {
            return ResponseEntity.status(401).body(List.of());
        }
        var tokenHolder = new ClientTokenHolder(token);
        var cards = gameService(tokenHolder).getPlayersCards();
        response.addCookie(createCookie(tokenHolder));
        return ResponseEntity.ok(cards != null ? cards : List.of());
    }

    @GetMapping("/state")
    @ResponseBody
    public ResponseEntity<String> getGameState(
            @CookieValue(name = "refreshToken", required = false) String token,
            @RequestParam("gameId") UUID gameId,
            HttpServletResponse response
    ) {
        if (token == null || token.isBlank()) {
            return ResponseEntity.status(401).body("");
        }
        var tokenHolder = new ClientTokenHolder(token);
        try {
            var headers = authJsonHeaders(tokenHolder);
            var res = restTemplate.exchange(
                    serverUrl.endsWith("/") ? serverUrl + "api/games/" + gameId : serverUrl + "/api/games/" + gameId,
                    org.springframework.http.HttpMethod.GET,
                    new HttpEntity<>(headers),
                    String.class
            );
            updateTokenFromHeaders(tokenHolder, res.getHeaders());
            response.addCookie(createCookie(tokenHolder));
            return ResponseEntity.status(res.getStatusCode()).body(res.getBody());
        } catch (HttpClientErrorException ex) {
            response.addCookie(createCookie(tokenHolder));
            return ResponseEntity.status(ex.getStatusCode()).body(ex.getResponseBodyAsString());
        }
    }

    private LobbyService lobbyService(ClientTokenHolder tokenHolder) {
        return new LobbyService(restTemplate, serverUrl, tokenHolder);
    }

    private GameService gameService(ClientTokenHolder tokenHolder) {
        return new GameService(restTemplate, serverUrl, tokenHolder);
    }

    private GameLobbyDTO findLobby(UUID lobbyId, ClientTokenHolder tokenHolder) {
        try {
            var lobbies = lobbyService(tokenHolder).getLobbies();
            if (lobbies == null) {
                return null;
            }
            return lobbies.stream()
                    .filter(l -> lobbyId.equals(l.getId()))
                    .findFirst()
                    .orElse(null);
        } catch (HttpClientErrorException | HttpMessageConversionException ex) {
            return null;
        }
    }

    private String serverWsUrl() {
        var wsUrl = rawServerUrl.replaceFirst("^http", "ws");
        if (!wsUrl.endsWith("/")) {
            wsUrl += "/";
        }
        return wsUrl + "ws";
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

    private String resolveBriskulaPreset(BriskulaGameConfigDTO config) {
        if (config == null || config.getNumberOfPlayers() == null) {
            return null;
        }
        int players = config.getNumberOfPlayers();
        if (players == 2) {
            return config.getCardsInHandNum() != null && config.getCardsInHandNum() == 4
                    ? "TWO_PLAYERS_FOUR_CARDS_IN_HAND_EACH"
                    : "TWO_PLAYERS";
        }
        if (players == 3) {
            return "THREE_PLAYERS";
        }
        if (players == 4) {
            return Boolean.TRUE.equals(config.getTeamsEnabled())
                    ? "FOUR_PLAYERS_WITH_TEAMS"
                    : "FOUR_PLAYERS_NO_TEAMS";
        }
        return null;
    }

    private Cookie createCookie(ClientTokenHolder tokenHolder) {
        var cookie = new Cookie("refreshToken", tokenHolder.getToken());
        cookie.setPath("/");
        cookie.setMaxAge(60 * 60 * 24);
        cookie.setHttpOnly(true);
        return cookie;
    }

    private HttpHeaders authJsonHeaders(ClientTokenHolder tokenHolder) {
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (tokenHolder != null && tokenHolder.getToken() != null) {
            headers.add(HttpHeaders.COOKIE, "refreshToken=" + tokenHolder.getToken());
        }
        return headers;
    }

    private void updateTokenFromHeaders(ClientTokenHolder tokenHolder, HttpHeaders headers) {
        if (tokenHolder == null || headers == null) return;
        var cookies = headers.get(HttpHeaders.SET_COOKIE);
        if (cookies == null) return;
        for (var cookie : cookies) {
            if (cookie.startsWith("refreshToken=")) {
                var value = cookie.split(";", 2)[0].split("=", 2);
                if (value.length == 2) {
                    tokenHolder.setToken(value[1]);
                }
                break;
            }
        }
    }
}
