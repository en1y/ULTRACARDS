package com.ultracards.server.dto;

import com.ultracards.server.entity.Role;
import com.ultracards.server.entity.UserEntity;

public class AuthResponseDTO {
    private String token;
    private String email;
    private String username;
    private Role role;

    public AuthResponseDTO() {}

    public AuthResponseDTO(String token, UserEntity user) {
        this.token = token;
        this.email = user.getEmail();
        this.username = user.getUsername();
        this.role = user.getRole();
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

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }
}
