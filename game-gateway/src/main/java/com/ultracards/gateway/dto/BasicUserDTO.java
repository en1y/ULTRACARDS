package com.ultracards.gateway.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class BasicUserDTO {
    @NotNull
    private Long userId;
    @NotBlank
    private String token;

    public BasicUserDTO() {
    }

    public BasicUserDTO(Long userId, String token) {
        this.userId = userId;
        this.token = token;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public Long getUserId() {
        return userId;
    }

    public String getToken() {
        return token;
    }
}
