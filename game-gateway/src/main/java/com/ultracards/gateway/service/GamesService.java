package com.ultracards.gateway.service;

import com.ultracards.gateway.dto.games.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.UUID;

@Service
public class GamesService {

    private final RestTemplate restTemplate;
    private final String serverUrl;

    @Autowired
    public GamesService(RestTemplate restTemplate, @Qualifier("serverUrl") String serverUrl) {
        this.restTemplate = restTemplate;
        this.serverUrl = serverUrl.endsWith("/") ? serverUrl : serverUrl + "/";
    }

    public LobbyDTO createLobby(ClientTokenHolder tokenHolder, LobbyCreateRequest request) {
        var entity = new HttpEntity<>(request, jsonHeaders(tokenHolder));
        var res = restTemplate.postForEntity(serverUrl + "api/games/lobbies", entity, LobbyDTO.class);
        updateToken(tokenHolder, res);
        return res.getBody();
    }

    public LobbyDTO joinLobby(ClientTokenHolder tokenHolder, UUID lobbyId) {
        var entity = new HttpEntity<>(new LobbyJoinRequest(lobbyId), jsonHeaders(tokenHolder));
        var res = restTemplate.postForEntity(serverUrl + "api/games/lobbies/join", entity, LobbyDTO.class);
        updateToken(tokenHolder, res);
        return res.getBody();
    }

    public LobbyDTO kickPlayer(ClientTokenHolder tokenHolder, UUID lobbyId, Long playerId) {
        var entity = new HttpEntity<>(new LobbyKickRequest(lobbyId, playerId), jsonHeaders(tokenHolder));
        var res = restTemplate.postForEntity(serverUrl + "api/games/lobbies/" + lobbyId + "/kick", entity, LobbyDTO.class);
        updateToken(tokenHolder, res);
        return res.getBody();
    }

    public void deleteLobby(ClientTokenHolder tokenHolder, UUID lobbyId) {
        var res = restTemplate.exchange(
                serverUrl + "api/games/lobbies/" + lobbyId,
                HttpMethod.DELETE,
                new HttpEntity<>(authHeaders(tokenHolder)),
                Void.class
        );
        updateToken(tokenHolder, res);
    }

    public void leaveLobby(ClientTokenHolder tokenHolder, UUID lobbyId) {
        var res = restTemplate.exchange(
                serverUrl + "api/games/lobbies/" + lobbyId + "/leave",
                HttpMethod.POST,
                new HttpEntity<>(authHeaders(tokenHolder)),
                Void.class
        );
        updateToken(tokenHolder, res);
    }

    public List<LobbyDTO> listLobbies(ClientTokenHolder tokenHolder) {
        var res = restTemplate.exchange(
                serverUrl + "api/games/lobbies",
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(tokenHolder)),
                new org.springframework.core.ParameterizedTypeReference<List<LobbyDTO>>() {}
        );
        updateToken(tokenHolder, res);
        return res.getBody();
    }

    public GameDTO startGame(ClientTokenHolder tokenHolder, StartGameRequest request) {
        var res = restTemplate.postForEntity(serverUrl + "api/games/start", new HttpEntity<>(request, jsonHeaders(tokenHolder)), GameDTO.class);
        updateToken(tokenHolder, res);
        return res.getBody();
    }

    public LobbyDTO updateLobbySettings(ClientTokenHolder tokenHolder, UUID lobbyId, LobbyCreateRequest req) {
        var res = restTemplate.exchange(
                serverUrl + "api/games/lobbies/" + lobbyId + "/settings",
                HttpMethod.PUT,
                new HttpEntity<>(req, authHeaders(tokenHolder)),
                LobbyDTO.class
        );
        updateToken(tokenHolder, res);
        return res.getBody();
    }

    public GameDTO getGame(ClientTokenHolder tokenHolder, UUID gameId) {
        var res = restTemplate.exchange(serverUrl + "api/games/" + gameId, HttpMethod.GET, new HttpEntity<>(authHeaders(tokenHolder)), GameDTO.class);
        updateToken(tokenHolder, res);
        return res.getBody();
    }

    public void finishGame(ClientTokenHolder tokenHolder, GameResultDTO result) {
        var res = restTemplate.postForEntity(serverUrl + "api/games/finish", new HttpEntity<>(result, jsonHeaders(tokenHolder)), Void.class);
        updateToken(tokenHolder, res);
    }

    private HttpHeaders authHeaders(ClientTokenHolder tokenHolder) {
        var h = new HttpHeaders();
        if (tokenHolder != null && tokenHolder.getToken() != null) {
            h.add(HttpHeaders.COOKIE, "refreshToken=" + tokenHolder.getToken());
        }
        return h;
    }

    private HttpHeaders jsonHeaders(ClientTokenHolder tokenHolder) {
        var h = authHeaders(tokenHolder);
        h.set(HttpHeaders.CONTENT_TYPE, org.springframework.http.MediaType.APPLICATION_JSON_VALUE);
        return h;
    }

    private void updateToken(ClientTokenHolder holder, ResponseEntity<?> res) {
        if (holder == null) return;
        var cookies = res.getHeaders().get(HttpHeaders.SET_COOKIE);
        if (cookies == null) return;
        for (var cookie : cookies) {
            if (cookie.startsWith("refreshToken=")) {
                holder.setToken(cookie.split(";", 2)[0].split("=", 2)[1]);
                break;
            }
        }
    }
}
