package com.ultracards.gateway.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;

@Valid
public class UserDTO {

    private Long id;
    private String username;
    private String role;
    @Email
    private String email;
    private boolean isEnabled;

    public UserDTO() {
    }

    public UserDTO(Long id, String username, String role, String email, boolean isEnabled) {
        this.id = id;
        this.username = username;
        this.role = role;
        this.email = email;
        this.isEnabled = isEnabled;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public boolean isEnabled() {
        return isEnabled;
    }

    public void setEnabled(boolean enabled) {
        isEnabled = enabled;
    }
}
