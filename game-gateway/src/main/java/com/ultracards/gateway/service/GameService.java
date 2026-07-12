package com.ultracards.gateway.service;

import com.ultracards.gateway.dto.games.games.GameEntityDTO;
import com.ultracards.gateway.dto.games.games.ShortGameHistoryDTO;
import com.ultracards.gateway.dto.games.games.GameSnapshotDTO;
import com.ultracards.gateway.dto.games.games.briskula.BriskulaGameHistoryDTO;
import com.ultracards.gateway.dto.games.games.briskula.BriskulaGameEntityDTO;
import com.ultracards.gateway.dto.games.games.treseta.TresetaGameHistoryDTO;
import com.ultracards.gateway.dto.games.games.treseta.TresetaGameEntityDTO;
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

    public BriskulaGameHistoryDTO getBriskulaGameHistory(UUID gameId) {
        var response = restTemplate.exchange(
                serverUrl + "api/games/history/briskula/" + gameId,
                HttpMethod.GET,
                new HttpEntity<>(tokenManager.authHeaders(tokenHolder)),
                BriskulaGameHistoryDTO.class
        );
        tokenManager.updateToken(tokenHolder, response);
        return response.getBody();
    }

    public TresetaGameHistoryDTO getTresetaGameHistory(UUID gameId) {
        var response = restTemplate.exchange(
                serverUrl + "api/games/history/treseta/" + gameId,
                HttpMethod.GET,
                new HttpEntity<>(tokenManager.authHeaders(tokenHolder)),
                TresetaGameHistoryDTO.class
        );
        tokenManager.updateToken(tokenHolder, response);
        return response.getBody();
    }

    public GameEntityDTO getGameByLobby(UUID lobbyId) {
        return getGameByLobby(lobbyId, GameEntityDTO.class);
    }

    public BriskulaGameEntityDTO getBriskulaGameByLobby(UUID lobbyId) {
        return getGameByLobby(lobbyId, BriskulaGameEntityDTO.class);
    }

    public TresetaGameEntityDTO getTresetaGameByLobby(UUID lobbyId) {
        return getGameByLobby(lobbyId, TresetaGameEntityDTO.class);
    }

    public GameSnapshotDTO<BriskulaGameEntityDTO> getBriskulaSnapshot(UUID gameId) {
        return getSnapshot(gameId, "briskula",
                new ParameterizedTypeReference<GameSnapshotDTO<BriskulaGameEntityDTO>>() {});
    }

    public GameSnapshotDTO<TresetaGameEntityDTO> getTresetaSnapshot(UUID gameId) {
        return getSnapshot(gameId, "treseta",
                new ParameterizedTypeReference<GameSnapshotDTO<TresetaGameEntityDTO>>() {});
    }

    private <T extends GameEntityDTO> GameSnapshotDTO<T> getSnapshot(
            UUID gameId, String gameType, ParameterizedTypeReference<GameSnapshotDTO<T>> responseType) {
        var response = restTemplate.exchange(
                serverUrl + "api/games/" + gameId + "/snapshot/" + gameType,
                HttpMethod.GET,
                new HttpEntity<>(tokenManager.authHeaders(tokenHolder)),
                responseType
        );
        tokenManager.updateToken(tokenHolder, response);
        return response.getBody();
    }

    private <T extends GameEntityDTO> T getGameByLobby(UUID lobbyId, Class<T> responseType) {
        var response = restTemplate.exchange(
                serverUrl + "api/games/lobby/" + lobbyId,
                HttpMethod.GET,
                new HttpEntity<>(tokenManager.authHeaders(tokenHolder)),
                responseType
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
