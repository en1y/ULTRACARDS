package com.ultracards.gateway.service;

import com.ultracards.gateway.dto.auth.UsernameDTO;
import jakarta.validation.constraints.NotBlank;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class AuthService {

    private final RestTemplate restTemplate;
    private final String serverBaseUrl;

    @Autowired
    // You still have to initiate it if you are not using Spring
    // If you are using Spring you can initiate the serverBaseUrl bean
    public AuthService(RestTemplate restTemplate,
                             @Qualifier("serverBaseUrl") String serverBaseUrl) {
        this.restTemplate = restTemplate;
        this.serverBaseUrl = serverBaseUrl;
    }

    public UsernameDTO updateUsername (@NotBlank String token, @NotBlank String username) {
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(HttpHeaders.COOKIE, "token=" + token);

        var entity = new HttpEntity<>(new UsernameDTO(username), headers);
        return restTemplate.exchange(
                serverBaseUrl + "/api/auth/username",
                HttpMethod.PUT,
                entity,
                UsernameDTO.class
        ).getBody();
    }
}
