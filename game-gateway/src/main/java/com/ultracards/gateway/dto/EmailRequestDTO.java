package com.ultracards.gateway.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public class EmailRequestDTO {
    @Email
    @NotBlank
    private String email;
    private String username;

    public EmailRequestDTO() {}

    public EmailRequestDTO(String email) {
        this.email = email;
    }

    public EmailRequestDTO(String email, String username) {
        this.email = email;
        this.username = username;
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
}