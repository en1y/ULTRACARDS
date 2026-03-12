package com.ultracards.gateway.service;

import lombok.Getter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

/**
 * Shared helper for attaching/refreshing the refresh token across gateway services.
 * Package-private so only gateway services can use it.
 */
@Component
public class TokenManager {

    @Getter private final ClientTokenHolder clientTokenHolder;

    TokenManager(ClientTokenHolder clientTokenHolder) {
        this.clientTokenHolder = clientTokenHolder;
    }

    ClientTokenHolder resolve(ClientTokenHolder override) {
        return override != null ? override : clientTokenHolder;
    }

    HttpHeaders authHeaders(ClientTokenHolder override) {
        var h = new HttpHeaders();
        var holder = resolve(override);
        if (holder != null && holder.getToken() != null) {
            h.add(HttpHeaders.COOKIE, "refreshToken=" + holder.getToken());
        }
        return h;
    }

    HttpHeaders jsonHeaders(ClientTokenHolder override) {
        var h = authHeaders(override);
        h.setContentType(MediaType.APPLICATION_JSON);
        return h;
    }

    void updateToken(ClientTokenHolder override, ResponseEntity<?> res) {
        if (res == null) return;
        updateToken(override, res.getHeaders());
    }

    void updateToken(ClientTokenHolder override, HttpHeaders headers) {
        var holder = resolve(override);
        if (holder == null || headers == null) return;
        var cookies = headers.get(HttpHeaders.SET_COOKIE);
        if (cookies == null) return;
        for (var cookie : cookies) {
            if (cookie.startsWith("refreshToken=")) {
                holder.setToken(cookie.split(";", 2)[0].split("=", 2)[1]);
                break;
            }
        }
    }

    String tokenValue(ClientTokenHolder override) {
        var holder = resolve(override);
        return holder != null ? holder.getToken() : null;
    }
}
