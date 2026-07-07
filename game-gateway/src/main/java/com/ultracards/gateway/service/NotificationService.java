package com.ultracards.gateway.service;

import com.ultracards.gateway.dto.notifications.NotificationDTO;
import jakarta.validation.constraints.NotBlank;
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
public class NotificationService {
    private final RestTemplate restTemplate;
    private final String serverUrl;
    private final ClientTokenHolder tokenHolder;
    private final TokenManager tokenManager;

    @Autowired
    public NotificationService(RestTemplate restTemplate,
                               @Qualifier("serverUrl") String serverUrl,
                               ClientTokenHolder tokenHolder,
                               TokenManager tokenManager) {
        this.restTemplate = restTemplate;
        this.serverUrl = serverUrl.endsWith("/") ? serverUrl : serverUrl + "/";
        this.tokenHolder = tokenHolder;
        this.tokenManager = tokenManager;
    }

    public NotificationService(RestTemplate restTemplate,
                               @Qualifier("serverUrl") String serverUrl,
                               ClientTokenHolder tokenHolder) {
        this(restTemplate, serverUrl, tokenHolder, new TokenManager(tokenHolder));
    }

    public List<NotificationDTO> getNotifications() {
        return getNotifications("");
    }

    public List<NotificationDTO> getUnreadNotifications() {
        return getNotifications("/unread");
    }

    public NotificationDTO sendTextNotificationToUser(Long recipientUserId, @NotBlank String message) {
        var response = restTemplate.postForEntity(
                serverUrl + "api/notifications/text/users/" + recipientUserId,
                new HttpEntity<>(message, tokenManager.jsonHeaders(tokenHolder)),
                NotificationDTO.class
        );
        tokenManager.updateToken(tokenHolder, response);
        return response.getBody();
    }

    public List<NotificationDTO> sendTextNotificationToAll(@NotBlank String message) {
        var response = restTemplate.exchange(
                serverUrl + "api/notifications/text/all",
                HttpMethod.POST,
                new HttpEntity<>(message, tokenManager.jsonHeaders(tokenHolder)),
                new ParameterizedTypeReference<List<NotificationDTO>>() {}
        );
        tokenManager.updateToken(tokenHolder, response);
        return response.getBody();
    }

    public NotificationDTO markRead(UUID id) {
        return patch(id, "read");
    }

    public NotificationDTO markUnread(UUID id) {
        return patch(id, "unread");
    }

    public boolean deleteNotification(UUID id) {
        var response = restTemplate.exchange(
                serverUrl + "api/notifications/" + id,
                HttpMethod.DELETE,
                new HttpEntity<>(tokenManager.authHeaders(tokenHolder)),
                Void.class
        );
        tokenManager.updateToken(tokenHolder, response);
        return response.getStatusCode().is2xxSuccessful();
    }

    private List<NotificationDTO> getNotifications(String path) {
        var response = restTemplate.exchange(
                serverUrl + "api/notifications" + path,
                HttpMethod.GET,
                new HttpEntity<>(tokenManager.authHeaders(tokenHolder)),
                new ParameterizedTypeReference<List<NotificationDTO>>() {}
        );
        tokenManager.updateToken(tokenHolder, response);
        return response.getBody();
    }

    private NotificationDTO patch(UUID id, String action) {
        var response = restTemplate.exchange(
                serverUrl + "api/notifications/" + id + "/" + action,
                HttpMethod.PATCH,
                new HttpEntity<>(tokenManager.authHeaders(tokenHolder)),
                NotificationDTO.class
        );
        tokenManager.updateToken(tokenHolder, response);
        return response.getBody();
    }
}
