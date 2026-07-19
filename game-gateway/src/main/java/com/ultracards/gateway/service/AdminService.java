package com.ultracards.gateway.service;

import com.ultracards.gateway.dto.admin.*;
import com.ultracards.gateway.dto.notifications.NotificationDTO;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

public class AdminService {
    private final RestTemplate restTemplate;
    private final String baseUrl;
    private final ClientTokenHolder tokenHolder;
    private final TokenManager tokenManager;

    public AdminService(RestTemplate restTemplate, String serverUrl, ClientTokenHolder tokenHolder) {
        this.restTemplate = restTemplate;
        this.baseUrl = (serverUrl.endsWith("/") ? serverUrl : serverUrl + "/") + "api/admin/v1";
        this.tokenHolder = tokenHolder;
        this.tokenManager = new TokenManager(tokenHolder);
    }

    public AdminStatusDTO status() { return get("/system/status", AdminStatusDTO.class); }
    public AdminOverviewDTO overview() { return get("/reports/overview", AdminOverviewDTO.class); }
    public AdminDatabaseOverviewDTO database() { return get("/reports/database", AdminDatabaseOverviewDTO.class); }
    public AdminDashboardDTO dashboard() {
        var overview = overview();
        var status = status();
        AdminDatabaseOverviewDTO database;
        try {
            database = database();
        } catch (RuntimeException ignored) {
            database = null;
        }
        return new AdminDashboardDTO(overview, status, database);
    }
    public AdminUserSummaryDTO user(Long id) { return get("/users/" + id, AdminUserSummaryDTO.class); }
    public AdminStatsDTO stats(Long id) { return get("/stats/users/" + id, AdminStatsDTO.class); }
    public AdminRecordedGameDTO game(UUID id) { return get("/game-records/" + id, AdminRecordedGameDTO.class); }
    public AdminLobbyDTO lobby(UUID id) { return get("/lobbies/" + id, AdminLobbyDTO.class); }
    public AdminAuditEventDTO audit(UUID id) { return get("/audit/" + id, AdminAuditEventDTO.class); }

    public AdminPageDTO<AdminUserSummaryDTO> users(int page, int size) {
        return get("/users?page=" + page + "&size=" + size, new ParameterizedTypeReference<>() {});
    }
    public AdminPageDTO<AdminRecordedGameDTO> games(int page, int size) {
        return get("/reports/games?page=" + page + "&size=" + size, new ParameterizedTypeReference<>() {});
    }
    public AdminPageDTO<AdminUserSummaryDTO> reportUsers(int page, int size, String query, Boolean exact,
                                                         String status, String role, String sort, String direction) {
        return get("/reports/users?page=" + page + "&size=" + size + query("status", status)
                + query("role", role) + query("sort", sort) + query("direction", direction)
                + query("query", query) + query("exact", exact),
                new ParameterizedTypeReference<>() {});
    }
    public AdminPageDTO<AdminRecordedGameDTO> games(int page, int size, String gameType, Boolean completed,
                                                    String mode, String query, String sort, String direction) {
        return get("/reports/games?page=" + page + "&size=" + size + query("gameType", gameType)
                + query("completed", completed) + query("mode", mode) + query("query", query)
                + query("sort", sort) + query("direction", direction),
                new ParameterizedTypeReference<>() {});
    }
    public AdminPageDTO<AdminSessionDTO> sessions(int page, int size) {
        return get("/reports/sessions?page=" + page + "&size=" + size, new ParameterizedTypeReference<>() {});
    }
    public AdminPageDTO<AdminSessionDTO> sessions(int page, int size, UUID id, Long userId, Boolean valid,
                                                  String query, String sort, String direction) {
        return get("/reports/sessions?page=" + page + "&size=" + size + query("userId", userId)
                + query("id", id) + query("valid", valid) + query("query", query)
                + query("sort", sort) + query("direction", direction),
                new ParameterizedTypeReference<>() {});
    }
    public AdminPageDTO<AdminAuditEventDTO> audit(int page, int size, String targetType, String targetId) {
        return get("/audit?page=" + page + "&size=" + size + query("targetType", targetType)
                + query("targetId", targetId), new ParameterizedTypeReference<>() {});
    }
    public List<AdminLobbyDTO> lobbies() {
        return get("/lobbies", new ParameterizedTypeReference<>() {});
    }
    public List<AdminGameAvailabilityDTO> gameAvailability() {
        return get("/games", new ParameterizedTypeReference<>() {});
    }

    public AdminUserSummaryDTO patchUser(Long id, AdminUserPatchDTO patch) {
        return exchange("/users/" + id, HttpMethod.PATCH, patch, AdminUserSummaryDTO.class);
    }
    public AdminUserSummaryDTO grantRole(Long id, String role, String reason) {
        return exchange("/users/" + id + "/roles/" + encode(role) + "?reason=" + encode(reason), HttpMethod.PUT, null, AdminUserSummaryDTO.class);
    }
    public AdminUserSummaryDTO revokeRole(Long id, String role, String reason) {
        return exchange("/users/" + id + "/roles/" + encode(role) + "?reason=" + encode(reason), HttpMethod.DELETE, null, AdminUserSummaryDTO.class);
    }
    public void revokeSessions(Long id, String reason) {
        exchange("/users/" + id + "/sessions?reason=" + encode(reason), HttpMethod.DELETE, null, Void.class);
    }

    public AdminLobbyDTO patchLobby(UUID id, AdminLobbyPatchDTO patch) {
        return exchange("/lobbies/" + id, HttpMethod.PATCH, patch, AdminLobbyDTO.class);
    }
    public AdminLobbyDTO kickLobbyPlayer(UUID id, Long userId, String reason) {
        return exchange("/lobbies/" + id + "/players/" + userId + "?reason=" + encode(reason), HttpMethod.DELETE, null, AdminLobbyDTO.class);
    }
    public void closeLobby(UUID id, String reason) {
        exchange("/lobbies/" + id + "?reason=" + encode(reason), HttpMethod.DELETE, null, Void.class);
    }
    public AdminLobbyDTO extendLobby(UUID id, AdminLobbyExtendDTO request) {
        return exchange("/lobbies/" + id + "/extend", HttpMethod.POST, request, AdminLobbyDTO.class);
    }
    public AdminGameAvailabilityDTO patchGameAvailability(String game, AdminGameAvailabilityPatchDTO patch) {
        return exchange("/games/" + encode(game), HttpMethod.PATCH, patch, AdminGameAvailabilityDTO.class);
    }
    public AdminGameAvailabilityDTO resetGameAvailability(String game, String mode, String reason) {
        return exchange("/games/" + encode(game) + "?reason=" + encode(reason) + query("mode", mode),
                HttpMethod.DELETE, null, AdminGameAvailabilityDTO.class);
    }

    public AdminRecordedGameDTO patchGame(UUID id, AdminRecordedGamePatchDTO patch) {
        return exchange("/game-records/" + id, HttpMethod.PATCH, patch, AdminRecordedGameDTO.class);
    }
    public void deleteGame(UUID id, String reason) {
        exchange("/game-records/" + id + "?reason=" + encode(reason), HttpMethod.DELETE, null, Void.class);
    }
    public AdminStatsDiffDTO patchStats(Long userId, String gameType, String mode, AdminStatsPatchDTO patch) {
        return exchange("/stats/users/" + userId + "/" + encode(gameType) + "/" + encode(mode), HttpMethod.PATCH, patch, AdminStatsDiffDTO.class);
    }
    public AdminStatsDiffDTO rebuildStats(Long userId, String gameType, String reason, boolean dryRun) {
        var path = "/stats/users/" + userId + "/rebuild?reason=" + encode(reason) + "&dryRun=" + dryRun;
        if (gameType != null && !gameType.isBlank()) path += "&gameType=" + encode(gameType);
        return exchange(path, HttpMethod.POST, null, AdminStatsDiffDTO.class);
    }

    public NotificationDTO notifyUser(Long userId, AdminNotificationRequestDTO request) {
        return exchange("/notifications/users/" + userId, HttpMethod.POST, request, NotificationDTO.class);
    }
    public List<NotificationDTO> notifyAll(AdminNotificationRequestDTO request) {
        return exchange("/notifications/all", HttpMethod.POST, request, new ParameterizedTypeReference<>() {});
    }

    public AdminPageDTO<AdminTokenDTO> tokens(int page, int size, UUID id, Long userId, Boolean active) {
        return get("/reports/tokens?page=" + page + "&size=" + size + query("id", id)
                + query("userId", userId) + query("active", active), new ParameterizedTypeReference<>() {});
    }
    public AdminPageDTO<NotificationDTO> notifications(int page, int size, Long userId, String type,
                                                        Boolean read, String query) {
        return get("/database/notifications?page=" + page + "&size=" + size + query("userId", userId)
                + query("type", type) + query("read", read) + query("query", query),
                new ParameterizedTypeReference<>() {});
    }
    public NotificationDTO patchNotification(UUID id, AdminNotificationPatchDTO patch) {
        return exchange("/database/notifications/" + id, HttpMethod.PATCH, patch, NotificationDTO.class);
    }
    public void deleteNotification(UUID id, String reason) {
        exchange("/database/notifications/" + id + "?reason=" + encode(reason), HttpMethod.DELETE, null, Void.class);
    }
    public void expireSession(UUID id, String reason) {
        exchange("/sessions/" + id + "/expire?reason=" + encode(reason), HttpMethod.POST, null, Void.class);
    }
    public void deleteSession(UUID id, String reason) {
        exchange("/sessions/" + id + "?reason=" + encode(reason), HttpMethod.DELETE, null, Void.class);
    }
    public void undoAudit(UUID id, String reason) {
        exchange("/audit/" + id + "/undo?reason=" + encode(reason), HttpMethod.POST, null, Void.class);
    }

    private <T> T get(String path, Class<T> type) { return exchange(path, HttpMethod.GET, null, type); }
    private <T> T get(String path, ParameterizedTypeReference<T> type) { return exchange(path, HttpMethod.GET, null, type); }

    private <T> T exchange(String path, HttpMethod method, Object body, Class<T> type) {
        var headers = body == null ? tokenManager.authHeaders(tokenHolder) : tokenManager.jsonHeaders(tokenHolder);
        var response = restTemplate.exchange(baseUrl + path, method, new HttpEntity<>(body, headers), type);
        tokenManager.updateToken(tokenHolder, response);
        return response.getBody();
    }

    private <T> T exchange(String path, HttpMethod method, Object body, ParameterizedTypeReference<T> type) {
        var headers = body == null ? tokenManager.authHeaders(tokenHolder) : tokenManager.jsonHeaders(tokenHolder);
        var response = restTemplate.exchange(baseUrl + path, method, new HttpEntity<>(body, headers), type);
        tokenManager.updateToken(tokenHolder, response);
        return response.getBody();
    }

    private String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private String query(String name, Object value) {
        return value == null || value.toString().isBlank() ? "" : "&" + name + "=" + encode(value.toString());
    }
}
