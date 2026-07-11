package com.ultracards.gateway.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class CardImageService {
    private final RestTemplate restTemplate;
    private final String serverUrl;
    private final ClientTokenHolder tokenHolder;
    private final TokenManager tokenManager;

    @Autowired
    public CardImageService(RestTemplate restTemplate,
                            @Qualifier("serverUrl") String serverUrl,
                            ClientTokenHolder tokenHolder,
                            TokenManager tokenManager) {
        this.restTemplate = restTemplate;
        this.serverUrl = serverUrl.endsWith("/") ? serverUrl : serverUrl + "/";
        this.tokenHolder = tokenHolder;
        this.tokenManager = tokenManager;
    }

    public CardImageService(RestTemplate restTemplate,
                            @Qualifier("serverUrl") String serverUrl,
                            ClientTokenHolder tokenHolder) {
        this(restTemplate, serverUrl, tokenHolder, new TokenManager(tokenHolder));
    }

    public byte[] italianCard(String suit, String value) {
        return card("italian/" + suit + "/" + value);
    }

    public byte[] italianCardBack() {
        return card("italian/back");
    }

    public byte[] pokerCard(String suit, String value) {
        return card("poker/" + suit + "/" + value);
    }

    public byte[] pokerCardBack() {
        return card("poker/back");
    }

    private byte[] card(String path) {
        var response = restTemplate.exchange(
                serverUrl + "api/cards/" + path,
                HttpMethod.GET,
                new HttpEntity<>(tokenManager.authHeaders(tokenHolder)),
                byte[].class
        );
        tokenManager.updateToken(tokenHolder, response);
        return response.getBody();
    }
}
