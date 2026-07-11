package com.ultracards.gateway.service;

import com.ultracards.gateway.dto.games.games.GameEntityDTO;
import com.ultracards.gateway.dto.games.games.ShortGameHistoryDTO;
import com.ultracards.gateway.dto.games.games.briskula.BriskulaGameHistoryDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.UUID;

@Service
public class GameService {
    private final RestTemplate restTemplate;
    private final String serverUrl;
    private final ClientTokenHolder tokenHolder;
    private final TokenManager tokenManager;

    @Autowired
    public GameService(RestTemplate restTemplate,
                       @Qualifier("serverUrl") String serverUrl,
                       ClientTokenHolder tokenHolder,
                       TokenManager tokenManager) {
        this.restTemplate = restTemplate;
        this.serverUrl = serverUrl.endsWith("/") ? serverUrl : serverUrl + "/";
        this.tokenHolder = tokenHolder;
        this.tokenManager = tokenManager;
    }

    public GameService(RestTemplate restTemplate,
                       @Qualifier("serverUrl") String serverUrl,
                       ClientTokenHolder tokenHolder) {
        this(restTemplate, serverUrl, tokenHolder, new TokenManager(tokenHolder));
    }

    public BriskulaGameHistoryDTO getGame(UUID gameId) {
        return getGameHistory(gameId);
    }

    public BriskulaGameHistoryDTO getGameHistory(UUID gameId) {
        var response = restTemplate.exchange(
                serverUrl + "api/games/history/" + gameId,
                HttpMethod.GET,
                new HttpEntity<>(tokenManager.authHeaders(tokenHolder)),
                BriskulaGameHistoryDTO.class
        );
        tokenManager.updateToken(tokenHolder, response);
        return response.getBody();
    }

    public GameEntityDTO getGameByLobby(UUID lobbyId) {
        var response = restTemplate.exchange(
                serverUrl + "api/games/lobby/" + lobbyId,
                HttpMethod.GET,
                new HttpEntity<>(tokenManager.authHeaders(tokenHolder)),
                GameEntityDTO.class
        );
        tokenManager.updateToken(tokenHolder, response);
        return response.getBody();
    }

    public List<ShortGameHistoryDTO> getPastGames() {
        return getPastGames(0, "both", "latest");
    }

    public List<ShortGameHistoryDTO> getPastGames(int offset, String result, String timeSort) {
        var response = restTemplate.exchange(
                serverUrl + "api/games/history?offset=" + offset + "&result=" + result + "&timeSort=" + timeSort,
                HttpMethod.GET,
                new HttpEntity<>(tokenManager.authHeaders(tokenHolder)),
                new ParameterizedTypeReference<List<ShortGameHistoryDTO>>() {}
        );
        tokenManager.updateToken(tokenHolder, response);
        return response.getBody();
    }

    public boolean deleteGame() {
        var response = restTemplate.exchange(
                serverUrl + "api/games",
                HttpMethod.DELETE,
                new HttpEntity<>(tokenManager.authHeaders(tokenHolder)),
                Void.class
        );
        tokenManager.updateToken(tokenHolder, response);
        return response.getStatusCode().is2xxSuccessful();
    }
}
