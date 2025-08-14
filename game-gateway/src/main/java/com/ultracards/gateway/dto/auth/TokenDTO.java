package com.ultracards.gateway.dto.auth;

import jakarta.validation.constraints.NotBlank;

public class TokenDTO {
    @NotBlank
    private String token;

    public TokenDTO(String token) {
        this.token = token;
    }

    public TokenDTO() {
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }
}
