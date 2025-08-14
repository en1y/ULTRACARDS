package com.ultracards.gateway.service;

import com.ultracards.gateway.dto.AuthResponseDTO;
import com.ultracards.gateway.dto.EmailRequestDTO;
import com.ultracards.gateway.dto.VerifyCodeRequestDTO;
import com.ultracards.gateway.dto.auth.UsernameDTO;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class LegacyAuthService {

    private final RestTemplate restTemplate;
    private final String serverBaseUrl;

    @Autowired
    // You still have to initiate it if you are not using Spring
    // If you are using Spring you can initiate the serverBaseUrl bean
    public LegacyAuthService(RestTemplate restTemplate,
                             @Qualifier("serverBaseUrl") String serverBaseUrl) {
        this.restTemplate = restTemplate;
        this.serverBaseUrl = serverBaseUrl;
    }

    public void sendEmail (@NotBlank @Email String email) {
        var requestDTO = new EmailRequestDTO(email);
        restTemplate.postForEntity(
                serverBaseUrl + "/auth/authorize",
                requestDTO,
                Void.class);
    }

    public ResponseEntity<AuthResponseDTO> sendVerificationCode(
            @NotBlank @Email String email,
            @NotBlank String verificationCode
    ) {
        var requestDTO = new VerifyCodeRequestDTO(email, verificationCode);
        return restTemplate.postForEntity(
                serverBaseUrl + "/auth/verify",
                requestDTO,
                AuthResponseDTO.class);
    }

    public void setUsername(
            @NotBlank @Email String email,
            @NotBlank String username
    ) {
        EmailRequestDTO requestDTO = new EmailRequestDTO(email, username);

        restTemplate.postForEntity(
                serverBaseUrl + "/auth/set-username",
                requestDTO,
                Void.class);
    }

    public void logout(String refreshToken) {
        var headers = new HttpHeaders();
        headers.add("Cookie", "refreshToken=" + refreshToken);
        var entity = new HttpEntity<Void>(headers);
        restTemplate.exchange(
                serverBaseUrl + "/auth/logout",
                HttpMethod.POST,
                entity,
                Void.class);
    }

}
