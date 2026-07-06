package com.ultracards.gateway.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.UUID;

@Service
public class UiPageService {
    private final RestTemplate restTemplate;
    private final String serverUrl;
    private final ClientTokenHolder tokenHolder;
    private final TokenManager tokenManager;

    @Autowired
    public UiPageService(RestTemplate restTemplate,
                         @Qualifier("serverUrl") String serverUrl,
                         ClientTokenHolder tokenHolder,
                         TokenManager tokenManager) {
        this.restTemplate = restTemplate;
        this.serverUrl = serverUrl.endsWith("/") ? serverUrl : serverUrl + "/";
        this.tokenHolder = tokenHolder;
        this.tokenManager = tokenManager;
    }

    public UiPageService(RestTemplate restTemplate,
                         @Qualifier("serverUrl") String serverUrl,
                         ClientTokenHolder tokenHolder) {
        this(restTemplate, serverUrl, tokenHolder, new TokenManager(tokenHolder));
    }

    public String home() {
        return get("");
    }

    public String lobbies() {
        return get("lobbies");
    }

    public String game() {
        return get("game");
    }

    public String history() {
        return get("history");
    }

    public String gameHistory(UUID gameId) {
        return get("history/" + gameId);
    }

    public String profile() {
        return get("profile");
    }

    public String error401() {
        return get("errors/401");
    }

    public String error403() {
        return get("errors/403");
    }

    public String error404() {
        return get("errors/404");
    }

    public String error500() {
        return get("errors/500");
    }

    public String sitemap() {
        return get("sitemap.xml");
    }

    public String robots() {
        return get("robots.txt");
    }

    private String get(String path) {
        var response = restTemplate.exchange(
                serverUrl + path,
                HttpMethod.GET,
                new HttpEntity<>(tokenManager.authHeaders(tokenHolder)),
                String.class
        );
        tokenManager.updateToken(tokenHolder, response);
        return response.getBody();
    }
}
