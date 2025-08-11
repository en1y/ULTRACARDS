package com.ultracards.gateway.service;

import com.ultracards.gateway.dto.BasicUserDTO;
import com.ultracards.gateway.dto.UserDTO;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

@Service
public class UserService {

    private static final Log log = LogFactory.getLog(UserService.class);

    private RestTemplate restTemplate;
    private String serverBaseUrl;

    public UserService(RestTemplate restTemplate,
                       @Qualifier("serverBaseUrl") String serverBaseUrl) {
        this.restTemplate = restTemplate;
        this.serverBaseUrl = serverBaseUrl;
    }

//    TODO: implement this method
    /*public UserDTO getUserById(Long id, String token) {

    }*/

    public Boolean isUserValid(Long userId, String token) {

        if (userId == null || token == null) {
            return null;
        }

        var requestDTO = new BasicUserDTO(userId, token);
        var headers = createAuthHeaders(token);
        var entity = new HttpEntity<>(requestDTO, headers);

        try {
            var res = restTemplate.exchange(
                    serverBaseUrl + "/auth/user-active",
                    HttpMethod.POST,
                    entity,
                    ResponseEntity.class);

            return res.getStatusCode() == HttpStatus.OK;
        }
        catch (HttpClientErrorException e) {
            var statusCode = e.getStatusCode().value();

            if (statusCode == HttpStatus.OK.value()) {
                return true;
            }
            if (statusCode == HttpStatus.UNAUTHORIZED.value()) {
                log.warn(String.format("Someone tried to access user %d", userId));
            } if (statusCode == HttpStatus.I_AM_A_TEAPOT.value()) {
                log.warn(String.format("Someone tried to access user that does not exist with id: \"%d\"", userId));
            }
        }
        return false;
    }

    private HttpHeaders createAuthHeaders(String token) {
        var headers = new HttpHeaders();
        if (token != null && !token.isEmpty()) {
            headers.set("Authorization", "Bearer " + token);
        }
        return headers;
    }

}
