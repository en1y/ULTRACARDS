package com.ultracards.gateway.service;

import com.ultracards.gateway.dto.auth.ProfileDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;

@Service
public class UserSearchService {
    private final RestTemplate restTemplate;
    private final String serverUrl;

    @Autowired
    public UserSearchService(RestTemplate restTemplate,
                             @Qualifier("serverUrl") String serverUrl) {
        this.restTemplate = restTemplate;
        this.serverUrl = serverUrl.endsWith("/") ? serverUrl : serverUrl + "/";
    }

    public List<ProfileDTO> searchUsersByUsername(String username) {
        return searchUsersByUsername(username, null, null);
    }

    public List<ProfileDTO> searchUsersByUsername(String username, Integer lower, Integer higher) {
        return search("username", username, lower, higher);
    }

    public List<ProfileDTO> searchUsersById(String id) {
        return searchUsersById(id, null, null);
    }

    public List<ProfileDTO> searchUsersById(String id, Integer lower, Integer higher) {
        return search("id", id, lower, higher);
    }

    public ProfileDTO getUserProfile(Long id) {
        return restTemplate.getForObject(serverUrl + "api/users/" + id + "/profile", ProfileDTO.class);
    }

    private List<ProfileDTO> search(String type, String value, Integer lower, Integer higher) {
        var url = serverUrl + "api/users/search/" + type + "/" + value;
        if (lower != null && higher != null) {
            url += "?lower=" + lower + "&higher=" + higher;
        }
        var response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<List<ProfileDTO>>() {}
        );
        return response.getBody();
    }
}
