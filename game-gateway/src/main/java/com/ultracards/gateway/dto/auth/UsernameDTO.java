package com.ultracards.gateway.dto.auth;

import jakarta.validation.constraints.NotBlank;

public class UsernameDTO {
    @NotBlank
    private String username;

    public UsernameDTO() {
    }

    public UsernameDTO(String username) {
        this.username = username;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }
}
