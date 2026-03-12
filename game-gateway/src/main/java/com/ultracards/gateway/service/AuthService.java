package com.ultracards.gateway.service;

import com.ultracards.gateway.dto.EmailDTO;
import com.ultracards.gateway.dto.auth.ProfileDTO;
import com.ultracards.gateway.dto.auth.UsernameDTO;
import com.ultracards.gateway.dto.auth.VerificationCodeDTO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import javax.security.auth.login.AccountLockedException;

@Service
public class AuthService {

    private final RestTemplate restTemplate;
    private final String serverUrl;
    private final ClientTokenHolder clientTokenHolder;
    private final TokenManager tokenManager;

    @Autowired
    public AuthService(RestTemplate restTemplate,
                       @Qualifier("serverUrl") String serverUrl,
                       ClientTokenHolder clientTokenHolder,
                       TokenManager tokenManager) {
        this.restTemplate = restTemplate;
        this.serverUrl = serverUrl;
        this.clientTokenHolder = clientTokenHolder;
        this.tokenManager = tokenManager;
    }

    public AuthService(RestTemplate restTemplate,
                       @Qualifier("serverUrl") String serverUrl,
                       ClientTokenHolder clientTokenHolder) {
        this(restTemplate, serverUrl, clientTokenHolder, new TokenManager(clientTokenHolder));
    }

    // You still have to initiate it if you are not using Spring
    // If you are using Spring you can initiate the serverUrl bean
    public UsernameDTO updateUsername (
            @NotBlank String username) {
        return updateUsername(clientTokenHolder, username);
    }

    public UsernameDTO updateUsername (
            @NotNull ClientTokenHolder tokenHolder,
            @NotBlank String username) {
        var entity = new HttpEntity<>(new UsernameDTO(username), tokenManager.authHeaders(tokenHolder));
        var response = restTemplate.exchange(
                serverUrl + "api/auth/username",
                HttpMethod.PUT,
                entity,
                UsernameDTO.class
        );

        tokenManager.updateToken(tokenHolder, response);
        return response.getBody();
    }

    public UsernameDTO getUsername (@NotNull ClientTokenHolder tokenHolder) {
        var entity = new HttpEntity<>(tokenManager.authHeaders(tokenHolder));
        var response = restTemplate.exchange(
                serverUrl + "api/auth/username",
                HttpMethod.GET,
                entity,
                UsernameDTO.class
        );

        tokenManager.updateToken(tokenHolder, response);
        return response.getBody();
    }

    public UsernameDTO getUsername() {
        return getUsername(clientTokenHolder);
    }

    public void logout (@NotNull ClientTokenHolder tokenHolder) {
        var entity = new HttpEntity<>(tokenManager.authHeaders(tokenHolder));
        var response = restTemplate.exchange(
                serverUrl + "api/auth/logout",
                HttpMethod.POST,
                entity,
                Void.class
        );
        tokenManager.updateToken(tokenHolder, response);
    }


    /**
     * @param email - email to which the verification code will be sent
     */
    public void sendVerificationEmail(
            @NotBlank @Email String email,
            @NotNull ClientTokenHolder tokenHolder) throws AccountLockedException {
        var entity = new HttpEntity<>(new EmailDTO(email), tokenManager.jsonHeaders(tokenHolder));

        var res = restTemplate.postForEntity(
                serverUrl + "api/auth/email/send",
                entity,
                Void.class);

        if (res.getStatusCode() == HttpStatus.CONFLICT) {
            throw new AccountLockedException("Account with this email already exists");
        }
    }

    public void sendVerificationEmail(@NotBlank @Email String email) {
        var entity = new HttpEntity<>(new EmailDTO(email));

        restTemplate.postForEntity(
                serverUrl + "api/auth/email/send",
                entity,
                Void.class);
    }

    public boolean verifyCode(
            @Valid VerificationCodeDTO verificationCode,
            ClientTokenHolder tokenHolder
    ) {
        var entity = new HttpEntity<>(verificationCode, tokenManager.jsonHeaders(tokenHolder));

        var response = restTemplate.postForEntity(
                serverUrl + "api/auth/email/verify",
                entity,
                Void.class
        );

        tokenManager.updateToken(tokenHolder, response);

        return response.getStatusCode().is2xxSuccessful();
    }

    public ProfileDTO getProfile(@NotNull ClientTokenHolder tokenHolder) {
        var entity = new HttpEntity<>(tokenManager.authHeaders(tokenHolder));

        var response = restTemplate.exchange(
                serverUrl + "api/auth/profile",
                HttpMethod.GET,
                entity,
                ProfileDTO.class
        );

        tokenManager.updateToken(tokenHolder, response);

        return response.getBody();
    }

    public ProfileDTO getProfile() {
        return getProfile(clientTokenHolder);
    }

    public ProfileDTO updateProfile(
            @NotNull ClientTokenHolder tokenHolder,
            @Valid ProfileDTO profileDTO
    ) {
        var entity = new HttpEntity<>(profileDTO, tokenManager.jsonHeaders(tokenHolder));
        var response = restTemplate.postForEntity(
                serverUrl + "api/auth/profile",
                entity,
                ProfileDTO.class
        );

        tokenManager.updateToken(tokenHolder, response);

        return response.getBody();
    }

    public ProfileDTO updateProfile(@Valid ProfileDTO profileDTO) {
        return updateProfile(clientTokenHolder, profileDTO);
    }

}
