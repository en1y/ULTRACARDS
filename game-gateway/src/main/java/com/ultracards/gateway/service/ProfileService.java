package com.ultracards.gateway.service;

import com.ultracards.gateway.dto.auth.DetailedProfileStatsDTO;
import com.ultracards.gateway.dto.auth.ProfileDTO;
import com.ultracards.gateway.dto.auth.UserSessionDTO;
import com.ultracards.gateway.dto.auth.UsernameDTO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;

@Service
public class ProfileService {
    private final RestTemplate restTemplate;
    private final String serverUrl;
    private final ClientTokenHolder tokenHolder;
    private final TokenManager tokenManager;

    @Autowired
    public ProfileService(RestTemplate restTemplate,
                          @Qualifier("serverUrl") String serverUrl,
                          ClientTokenHolder tokenHolder,
                          TokenManager tokenManager) {
        this.restTemplate = restTemplate;
        this.serverUrl = serverUrl.endsWith("/") ? serverUrl : serverUrl + "/";
        this.tokenHolder = tokenHolder;
        this.tokenManager = tokenManager;
    }

    public ProfileService(RestTemplate restTemplate,
                          @Qualifier("serverUrl") String serverUrl,
                          ClientTokenHolder tokenHolder) {
        this(restTemplate, serverUrl, tokenHolder, new TokenManager(tokenHolder));
    }

    public UsernameDTO getUsername() {
        var response = restTemplate.exchange(
                serverUrl + "api/profile/username",
                HttpMethod.GET,
                new HttpEntity<>(tokenManager.authHeaders(tokenHolder)),
                UsernameDTO.class
        );
        tokenManager.updateToken(tokenHolder, response);
        return response.getBody();
    }

    public UsernameDTO updateUsername(@NotBlank String username) {
        var response = restTemplate.exchange(
                serverUrl + "api/profile/username",
                HttpMethod.PUT,
                new HttpEntity<>(new UsernameDTO(username), tokenManager.jsonHeaders(tokenHolder)),
                UsernameDTO.class
        );
        tokenManager.updateToken(tokenHolder, response);
        return response.getBody();
    }

    public ProfileDTO getProfile() {
        var response = restTemplate.exchange(
                serverUrl + "api/profile",
                HttpMethod.GET,
                new HttpEntity<>(tokenManager.authHeaders(tokenHolder)),
                ProfileDTO.class
        );
        tokenManager.updateToken(tokenHolder, response);
        return response.getBody();
    }

    public DetailedProfileStatsDTO getDetailedProfileStats() {
        var response = restTemplate.exchange(
                serverUrl + "api/profile/stats",
                HttpMethod.GET,
                new HttpEntity<>(tokenManager.authHeaders(tokenHolder)),
                DetailedProfileStatsDTO.class
        );
        tokenManager.updateToken(tokenHolder, response);
        return response.getBody();
    }

    public ProfileDTO updateProfile(@Valid ProfileDTO profileDTO) {
        var response = restTemplate.postForEntity(
                serverUrl + "api/profile",
                new HttpEntity<>(profileDTO, tokenManager.jsonHeaders(tokenHolder)),
                ProfileDTO.class
        );
        tokenManager.updateToken(tokenHolder, response);
        return response.getBody();
    }

    public List<UserSessionDTO> getSessions() {
        var response = restTemplate.exchange(
                serverUrl + "api/profile/sessions",
                HttpMethod.GET,
                new HttpEntity<>(tokenManager.authHeaders(tokenHolder)),
                new ParameterizedTypeReference<List<UserSessionDTO>>() {}
        );
        tokenManager.updateToken(tokenHolder, response);
        return response.getBody();
    }

    public boolean deleteSession(UserSessionDTO session) {
        var response = restTemplate.exchange(
                serverUrl + "api/profile/sessions",
                HttpMethod.DELETE,
                new HttpEntity<>(session, tokenManager.jsonHeaders(tokenHolder)),
                Void.class
        );
        tokenManager.updateToken(tokenHolder, response);
        return response.getStatusCode().is2xxSuccessful();
    }
}
