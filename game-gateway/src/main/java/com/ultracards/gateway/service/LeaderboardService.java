package com.ultracards.gateway.service;

import com.ultracards.gateway.dto.games.GameTypeDTO;
import com.ultracards.gateway.dto.leaderboard.LeaderboardMetricDTO;
import com.ultracards.gateway.dto.leaderboard.LeaderboardPageDTO;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

public class LeaderboardService {
    private final RestTemplate restTemplate;
    private final String serverUrl;
    private final ClientTokenHolder tokenHolder;
    private final TokenManager tokenManager;

    public LeaderboardService(RestTemplate restTemplate, String serverUrl, ClientTokenHolder tokenHolder) {
        this.restTemplate = restTemplate;
        this.serverUrl = serverUrl.endsWith("/") ? serverUrl : serverUrl + "/";
        this.tokenHolder = tokenHolder;
        this.tokenManager = new TokenManager(tokenHolder);
    }

    public LeaderboardPageDTO get(LeaderboardMetricDTO metric, GameTypeDTO gameType, String mode,
                                  int page, int size) {
        var builder = UriComponentsBuilder.fromUriString(serverUrl + "api/leaderboards")
                .queryParam("metric", metric)
                .queryParam("page", page)
                .queryParam("size", size);
        if (gameType != null) builder.queryParam("gameType", gameType);
        if (mode != null && !mode.isBlank()) builder.queryParam("mode", mode);

        var response = restTemplate.exchange(
                builder.build().encode().toUri(),
                HttpMethod.GET,
                new HttpEntity<>(tokenManager.authHeaders(tokenHolder)),
                LeaderboardPageDTO.class
        );
        tokenManager.updateToken(tokenHolder, response);
        return response.getBody();
    }
}
