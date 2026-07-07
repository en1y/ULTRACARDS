package com.ultracards.gateway.service;

import com.ultracards.gateway.dto.games.chat.ChatDTO;
import com.ultracards.gateway.dto.games.chat.ChatMessageDTO;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class ChatService {
    private final RestTemplate restTemplate;
    private final String serverUrl;
    private final ClientTokenHolder tokenHolder;
    private final TokenManager tokenManager;

    @Autowired
    public ChatService(RestTemplate restTemplate,
                       @Qualifier("serverUrl") String serverUrl,
                       ClientTokenHolder tokenHolder,
                       TokenManager tokenManager) {
        this.restTemplate = restTemplate;
        this.serverUrl = serverUrl.endsWith("/") ? serverUrl : serverUrl + "/";
        this.tokenHolder = tokenHolder;
        this.tokenManager = tokenManager;
    }

    public ChatService(RestTemplate restTemplate,
                       @Qualifier("serverUrl") String serverUrl,
                       ClientTokenHolder tokenHolder) {
        this(restTemplate, serverUrl, tokenHolder, new TokenManager(tokenHolder));
    }

    public ChatDTO getLobbyChat() {
        var response = restTemplate.exchange(
                serverUrl + "api/chat",
                HttpMethod.GET,
                new HttpEntity<>(tokenManager.authHeaders(tokenHolder)),
                ChatDTO.class
        );
        tokenManager.updateToken(tokenHolder, response);
        return response.getBody();
    }

    public boolean sendLobbyMessage(@Valid ChatMessageDTO message) {
        var response = restTemplate.postForEntity(
                serverUrl + "api/chat",
                new HttpEntity<>(message, tokenManager.jsonHeaders(tokenHolder)),
                Void.class
        );
        tokenManager.updateToken(tokenHolder, response);
        return response.getStatusCode().is2xxSuccessful();
    }

    public ChatDTO getFriendChat(Long friendUserId) {
        var response = restTemplate.exchange(
                serverUrl + "api/chat/friends/" + friendUserId,
                HttpMethod.GET,
                new HttpEntity<>(tokenManager.authHeaders(tokenHolder)),
                ChatDTO.class
        );
        tokenManager.updateToken(tokenHolder, response);
        return response.getBody();
    }

    public ChatDTO sendFriendMessage(Long friendUserId, @Valid ChatMessageDTO message) {
        var response = restTemplate.postForEntity(
                serverUrl + "api/chat/friends/" + friendUserId,
                new HttpEntity<>(message, tokenManager.jsonHeaders(tokenHolder)),
                ChatDTO.class
        );
        tokenManager.updateToken(tokenHolder, response);
        return response.getBody();
    }

    public ChatDTO readAllFriendMessages(Long friendUserId) {
        var response = restTemplate.postForEntity(
                serverUrl + "api/chat/friends/" + friendUserId + "/read-all",
                new HttpEntity<>(tokenManager.authHeaders(tokenHolder)),
                ChatDTO.class
        );
        tokenManager.updateToken(tokenHolder, response);
        return response.getBody();
    }
}
