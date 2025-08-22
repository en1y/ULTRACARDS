package com.ultracards.gateway.service;

import com.ultracards.gateway.dto.EmailDTO;
import com.ultracards.gateway.dto.auth.ProfileDTO;
import com.ultracards.gateway.dto.auth.UsernameDTO;
import com.ultracards.gateway.dto.auth.VerificationCodeDTO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.stream.Collectors;

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

    public UsernameDTO updateUsername (
            @NotNull HttpHeaders headers,
            @NotBlank String username) {
        var entity = new HttpEntity<>(new UsernameDTO(username), headers);
        var response = restTemplate.exchange(
                serverBaseUrl + "/api/auth/username",
                HttpMethod.PUT,
                entity,
                UsernameDTO.class
        );

        updateHeaders(headers, response.getHeaders());
        return response.getBody();
    }

    public UsernameDTO getUsername (@NotNull HttpHeaders headers) {
        var entity = new HttpEntity<>(headers);
        var response = restTemplate.exchange(
                serverBaseUrl + "/api/auth/username",
                HttpMethod.GET,
                entity,
                UsernameDTO.class
        );

        updateHeaders(headers, response.getHeaders());

        return response.getBody();
    }

    public void logout (@NotNull HttpHeaders headers) {
        var entity = new HttpEntity<>(headers);
        var response = restTemplate.exchange(
                serverBaseUrl + "/api/auth/logout",
                HttpMethod.POST,
                entity,
                Void.class
        );
        updateHeaders(headers, response.getHeaders());
    }


    /**
     * @param email - email to which the verification code will be sent
     * @return HttpHeaders with session cookie. You should keep the HttpHeader for further usage
     */
    public HttpHeaders sendVerificationEmail(@NotBlank @Email String email) {
        var entity = new HttpEntity<>(new EmailDTO(email), new HttpHeaders());
        entity.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        var response = restTemplate.postForEntity(
                serverBaseUrl + "/api/auth/email/send",
                entity,
                Void.class);

        return response.getHeaders();
    }

    public boolean verifyCode(
            @NotBlank
            @Pattern(regexp = "\\d{6}", message = "Code must be exactly 6 digits")
            String verificationCode,
            @NotNull HttpHeaders headers
    ) {

        var entity = new HttpEntity<>(new VerificationCodeDTO(verificationCode), headers);
        entity.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        var response = restTemplate.postForEntity(
                serverBaseUrl + "/api/auth/email/verification",
                entity,
                Void.class);

        updateHeaders(headers, response.getHeaders());

        return response.getStatusCode().is2xxSuccessful();

    }

    public ProfileDTO getProfile(@NotNull HttpHeaders headers) {
        var entity = new HttpEntity<>(headers);

        var response = restTemplate.exchange(
                serverBaseUrl + "/api/auth/profile",
                HttpMethod.GET,
                entity,
                ProfileDTO.class
        );

        updateHeaders(headers, response.getHeaders());

        return response.getBody();
    }

    public ProfileDTO updateProfile(
            @NotNull HttpHeaders headers,
            @Valid ProfileDTO profileDTO
    ) {
        var entity = new HttpEntity<>(profileDTO, headers);
        var response = restTemplate.postForEntity(
                serverBaseUrl + "/api/auth/profile",
                entity,
                ProfileDTO.class
        );

        updateHeaders(headers, response.getHeaders());

        return response.getBody();
    }

    private void updateHeaders(HttpHeaders oldHeaders, HttpHeaders newHeaders) {
        var setCookies = newHeaders.get(HttpHeaders.SET_COOKIE);
        if (setCookies != null && !setCookies.isEmpty()) {
            var cookies = setCookies.stream()
                    .map(c -> c.split(";", 2)[0])
                    .collect(Collectors.joining("; "));
            oldHeaders.remove(HttpHeaders.COOKIE);
            oldHeaders.add(HttpHeaders.COOKIE, cookies);
        }
    }
}
