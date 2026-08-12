package com.ultracards.gateway.service;

import com.ultracards.gateway.dto.points.PointTransactionPageDTO;
import com.ultracards.gateway.dto.points.PointsAccountDTO;
import com.ultracards.gateway.dto.points.PointsClaimDTO;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.web.client.RestTemplate;

public class PointsService {
    private final RestTemplate restTemplate;
    private final String baseUrl;
    private final ClientTokenHolder tokenHolder;
    private final TokenManager tokenManager;

    public PointsService(RestTemplate restTemplate, String serverUrl, ClientTokenHolder tokenHolder) {
        this.restTemplate = restTemplate;
        this.baseUrl = (serverUrl.endsWith("/") ? serverUrl : serverUrl + "/") + "api/points";
        this.tokenHolder = tokenHolder;
        this.tokenManager = new TokenManager(tokenHolder);
    }

    public PointsAccountDTO account() {
        return exchange("", HttpMethod.GET, PointsAccountDTO.class);
    }

    public long balance() {
        var value = exchange("/balance", HttpMethod.GET, Long.class);
        return value == null ? 0 : value;
    }

    public PointsClaimDTO claimDaily() {
        return exchange("/daily-claim", HttpMethod.POST, PointsClaimDTO.class);
    }

    public PointTransactionPageDTO transactions(int page, int size) {
        return exchange("/transactions?page=" + page + "&size=" + size,
                HttpMethod.GET, PointTransactionPageDTO.class);
    }

    private <T> T exchange(String path, HttpMethod method, Class<T> type) {
        var response = restTemplate.exchange(baseUrl + path, method,
                new HttpEntity<>(tokenManager.authHeaders(tokenHolder)), type);
        tokenManager.updateToken(tokenHolder, response);
        return response.getBody();
    }
}
