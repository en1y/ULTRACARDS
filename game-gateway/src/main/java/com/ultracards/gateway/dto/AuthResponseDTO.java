package com.ultracards.gateway.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public class AuthResponseDTO {
    private String token;
    @NotBlank
    @Email(message = "Email format is invalid")
    private String email;
    private String username;
    private String role;
    private Long userId;

    public AuthResponseDTO() {}

    public AuthResponseDTO(String token, String email, String username, String role, Long userId) {
        this.token = token;
        this.email = email;
        this.username = username;
        this.role = role;
        this.userId = userId;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }
}