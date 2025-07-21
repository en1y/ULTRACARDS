package com.ultracards.webui.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Data Transfer Object for authentication responses.
 * Matches the server's AuthResponseDTO structure.
 * Uses JsonIgnoreProperties to handle unknown properties during deserialization.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class AuthResponseDTO {
    private String token;
    private String email;
    private String username;
    private String role;

    public AuthResponseDTO() {}

    public AuthResponseDTO(String token, String email, String username, String role) {
        this.token = token;
        this.email = email;
        this.username = username;
        this.role = role;
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
}