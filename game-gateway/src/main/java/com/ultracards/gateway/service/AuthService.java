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

    @Autowired
    // You still have to initiate it if you are not using Spring
    // If you are using Spring you can initiate the serverUrl bean
    public AuthService(RestTemplate restTemplate,
                             @Qualifier("serverUrl") String serverUrl) {
        this.restTemplate = restTemplate;
        this.serverUrl = serverUrl;
    }

    public UsernameDTO updateUsername (
            @NotNull ClientTokenHolder tokenHolder,
            @NotBlank String username) {
        var entity = new HttpEntity<>(new UsernameDTO(username), createHeaders(tokenHolder.getToken()));
        var response = restTemplate.exchange(
                serverUrl + "api/auth/username",
                HttpMethod.PUT,
                entity,
                UsernameDTO.class
        );

        updateTokenHolder(tokenHolder, response.getHeaders());
        return response.getBody();
    }

    public UsernameDTO getUsername (@NotNull ClientTokenHolder tokenHolder) {
        var entity = new HttpEntity<>(createHeaders(tokenHolder.getToken()));
        var response = restTemplate.exchange(
                serverUrl + "api/auth/username",
                HttpMethod.GET,
                entity,
                UsernameDTO.class
        );

        updateTokenHolder(tokenHolder, response.getHeaders());
        return response.getBody();
    }

    public void logout (@NotNull ClientTokenHolder tokenHolder) {
        var entity = new HttpEntity<>(createHeaders(tokenHolder.getToken()));
        var response = restTemplate.exchange(
                serverUrl + "api/auth/logout",
                HttpMethod.POST,
                entity,
                Void.class
        );
        updateTokenHolder(tokenHolder, response.getHeaders());
    }


    /**
     * @param email - email to which the verification code will be sent
     */
    public void sendVerificationEmail(
            @NotBlank @Email String email,
            @NotNull ClientTokenHolder tokenHolder) throws AccountLockedException {
        var entity = new HttpEntity<>(new EmailDTO(email), createHeaders(tokenHolder.getToken()));

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
        var headers = tokenHolder.getToken() != null ? createHeaders(tokenHolder.getToken()) : new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        var entity = new HttpEntity<>(verificationCode, headers);

        var response = restTemplate.postForEntity(
                serverUrl + "api/auth/email/verify",
                entity,
                Void.class
        );

        updateTokenHolder(tokenHolder, response.getHeaders());

        return response.getStatusCode().is2xxSuccessful();
    }

    public ProfileDTO getProfile(@NotNull ClientTokenHolder tokenHolder) {
        var entity = new HttpEntity<>(createHeaders(tokenHolder.getToken()));

        var response = restTemplate.exchange(
                serverUrl + "api/auth/profile",
                HttpMethod.GET,
                entity,
                ProfileDTO.class
        );

        updateTokenHolder(tokenHolder, response.getHeaders());

        return response.getBody();
    }

    public ProfileDTO updateProfile(
            @NotNull ClientTokenHolder tokenHolder,
            @Valid ProfileDTO profileDTO
    ) {
        var entity = new HttpEntity<>(profileDTO, createHeaders(tokenHolder.getToken()));
        var response = restTemplate.postForEntity(
                serverUrl + "api/auth/profile",
                entity,
                ProfileDTO.class
        );

        updateTokenHolder(tokenHolder, response.getHeaders());

        return response.getBody();
    }

    private HttpHeaders createHeaders(String token) {
        var headers = new HttpHeaders();
        var cookie = ResponseCookie.from("refreshToken", token).build();
        headers.add(HttpHeaders.COOKIE, cookie.toString());
        return headers;
    }

    private void updateTokenHolder(@NotNull ClientTokenHolder tokenHolder, HttpHeaders headers) {
        var cookies = headers.get(HttpHeaders.SET_COOKIE);

        if (cookies != null && !cookies.isEmpty()) {
            for (var cookie : cookies) {
                if (cookie.startsWith("refreshToken=")) {
                    var refreshToken = cookie.split(";", 2)[0] // "refreshToken=abc123"
                            .split("=", 2)[1]; // "abc123"
                    tokenHolder.setToken(refreshToken);
                    return;
                }
            }
        }
    }
}
