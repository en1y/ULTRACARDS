package com.ultracards.gateway.service;

import com.ultracards.gateway.dto.games.lobby.GameLobbyDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.UUID;

@Service
public class LobbyService {
    private final RestTemplate restTemplate;
    private final String serverUrl;
    private final ClientTokenHolder tokenHolder;
    private final TokenManager tokenManager;

    @Autowired
    public LobbyService(RestTemplate restTemplate,
                       @Qualifier("serverUrl") String serverUrl,
                       ClientTokenHolder clientTokenHolder,
                       TokenManager tokenManager) {
        this.restTemplate = restTemplate;
        this.serverUrl = serverUrl.endsWith("/") ? serverUrl : serverUrl + "/";
        this.tokenHolder = clientTokenHolder;
        this.tokenManager = tokenManager;
    }

    public LobbyService(RestTemplate restTemplate,
                        @Qualifier("serverUrl") String serverUrl,
                        ClientTokenHolder clientTokenHolder) {
        this(restTemplate, serverUrl, clientTokenHolder, new TokenManager(clientTokenHolder));
    }

    public GameLobbyDTO createLobby(GameLobbyDTO gameLobbyDTO) {
        var res = restTemplate.postForEntity(
                serverUrl + "api/lobby/create",
                new HttpEntity<>(gameLobbyDTO, tokenManager.jsonHeaders(tokenHolder)),
                GameLobbyDTO.class
        );
        tokenManager.updateToken(tokenHolder, res);
        return res.getBody();
    }

    public boolean joinLobby(UUID lobbyId) {
        var res = restTemplate.postForEntity(
                serverUrl + "api/lobby/join",
                new HttpEntity<>(lobbyId, tokenManager.jsonHeaders(tokenHolder)),
                Boolean.class
        );
        tokenManager.updateToken(tokenHolder, res);
        return Boolean.TRUE.equals(res.getBody());
    }

    public boolean leaveLobby(UUID lobbyId) {
        var res = restTemplate.postForEntity(
                serverUrl + "api/lobby/leave",
                new HttpEntity<>(lobbyId, tokenManager.jsonHeaders(tokenHolder)),
                Boolean.class
        );
        tokenManager.updateToken(tokenHolder, res);
        return Boolean.TRUE.equals(res.getBody());
    }

    public boolean startLobby() {
        var res = restTemplate.postForEntity(
                serverUrl + "api/lobby/start",
                new HttpEntity<>(tokenManager.authHeaders(tokenHolder)),
                Boolean.class
        );
        tokenManager.updateToken(tokenHolder, res);
        return Boolean.TRUE.equals(res.getBody());
    }

    public GameLobbyDTO updateLobby(GameLobbyDTO lobbyDTO) {
        var res = restTemplate.exchange(
                serverUrl + "api/lobby/update",
                HttpMethod.PUT,
                new HttpEntity<>(lobbyDTO, tokenManager.jsonHeaders(tokenHolder)),
                GameLobbyDTO.class
        );
        tokenManager.updateToken(tokenHolder, res);
        return res.getBody();
    }

    public GameLobbyDTO kickPlayer(Long playerId) {
        var res = restTemplate.postForEntity(
                serverUrl + "api/lobby/kick-player",
                new HttpEntity<>(playerId, tokenManager.jsonHeaders(tokenHolder)),
                GameLobbyDTO.class
        );
        tokenManager.updateToken(tokenHolder, res);
        return res.getBody();
    }

    public boolean deleteLobby() {
        var res = restTemplate.exchange(
                serverUrl + "api/lobby/delete",
                HttpMethod.DELETE,
                new HttpEntity<>(tokenManager.authHeaders(tokenHolder)),
                Void.class
        );
        tokenManager.updateToken(tokenHolder, res);
        return res.getStatusCode().is2xxSuccessful();
    }

    public List<GameLobbyDTO> getLobbies() {
        var res = restTemplate.exchange(
                serverUrl + "api/lobby/get-lobbies",
                HttpMethod.GET,
                new HttpEntity<>(tokenManager.authHeaders(tokenHolder)),
                new org.springframework.core.ParameterizedTypeReference<List<GameLobbyDTO>>() {}
        );
        tokenManager.updateToken(tokenHolder, res);
        return res.getBody();
    }
}
