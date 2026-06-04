package com.ultracards.gateway.service;

import com.ultracards.gateway.dto.friends.FriendDTO;
import com.ultracards.gateway.dto.friends.FriendRequestDTO;
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
public class FriendService {
    private final RestTemplate restTemplate;
    private final String serverUrl;
    private final ClientTokenHolder tokenHolder;
    private final TokenManager tokenManager;

    @Autowired
    public FriendService(RestTemplate restTemplate,
                         @Qualifier("serverUrl") String serverUrl,
                         ClientTokenHolder tokenHolder,
                         TokenManager tokenManager) {
        this.restTemplate = restTemplate;
        this.serverUrl = serverUrl.endsWith("/") ? serverUrl : serverUrl + "/";
        this.tokenHolder = tokenHolder;
        this.tokenManager = tokenManager;
    }

    public FriendService(RestTemplate restTemplate,
                         @Qualifier("serverUrl") String serverUrl,
                         ClientTokenHolder tokenHolder) {
        this(restTemplate, serverUrl, tokenHolder, new TokenManager(tokenHolder));
    }

    public List<FriendDTO> getFriends() {
        var response = restTemplate.exchange(
                serverUrl + "api/friends",
                HttpMethod.GET,
                new HttpEntity<>(tokenManager.authHeaders(tokenHolder)),
                new ParameterizedTypeReference<List<FriendDTO>>() {}
        );
        tokenManager.updateToken(tokenHolder, response);
        return response.getBody();
    }

    public List<FriendDTO> getBlockedFriends() {
        var response = restTemplate.exchange(
                serverUrl + "api/friends/blocked",
                HttpMethod.GET,
                new HttpEntity<>(tokenManager.authHeaders(tokenHolder)),
                new ParameterizedTypeReference<List<FriendDTO>>() {}
        );
        tokenManager.updateToken(tokenHolder, response);
        return response.getBody();
    }

    public List<FriendRequestDTO> getIncomingFriendRequests() {
        return getFriendRequests("incoming");
    }

    public List<FriendRequestDTO> getOutgoingFriendRequests() {
        return getFriendRequests("outgoing");
    }

    public FriendRequestDTO sendFriendRequest(Long recipientUserId) {
        var response = restTemplate.postForEntity(
                serverUrl + "api/friends/requests/send/" + recipientUserId,
                new HttpEntity<>(tokenManager.authHeaders(tokenHolder)),
                FriendRequestDTO.class
        );
        tokenManager.updateToken(tokenHolder, response);
        return response.getBody();
    }

    public FriendRequestDTO acceptFriendRequest(UUID requestId) {
        return postRequestAction(requestId, "accept");
    }

    public FriendRequestDTO declineFriendRequest(UUID requestId) {
        return postRequestAction(requestId, "decline");
    }

    public FriendRequestDTO blockFriendRequest(UUID requestId) {
        return postRequestAction(requestId, "block");
    }

    public boolean removeFriend(Long friendUserId) {
        var response = restTemplate.exchange(
                serverUrl + "api/friends/" + friendUserId,
                HttpMethod.DELETE,
                new HttpEntity<>(tokenManager.authHeaders(tokenHolder)),
                Void.class
        );
        tokenManager.updateToken(tokenHolder, response);
        return response.getStatusCode().is2xxSuccessful();
    }

    public boolean unblockUser(Long blockedUserId) {
        var response = restTemplate.exchange(
                serverUrl + "api/friends/blocks/" + blockedUserId,
                HttpMethod.DELETE,
                new HttpEntity<>(tokenManager.authHeaders(tokenHolder)),
                Void.class
        );
        tokenManager.updateToken(tokenHolder, response);
        return response.getStatusCode().is2xxSuccessful();
    }

    private List<FriendRequestDTO> getFriendRequests(String direction) {
        var response = restTemplate.exchange(
                serverUrl + "api/friends/requests/" + direction,
                HttpMethod.GET,
                new HttpEntity<>(tokenManager.authHeaders(tokenHolder)),
                new ParameterizedTypeReference<List<FriendRequestDTO>>() {}
        );
        tokenManager.updateToken(tokenHolder, response);
        return response.getBody();
    }

    private FriendRequestDTO postRequestAction(UUID requestId, String action) {
        var response = restTemplate.postForEntity(
                serverUrl + "api/friends/requests/" + requestId + "/" + action,
                new HttpEntity<>(tokenManager.authHeaders(tokenHolder)),
                FriendRequestDTO.class
        );
        tokenManager.updateToken(tokenHolder, response);
        return response.getBody();
    }
}
