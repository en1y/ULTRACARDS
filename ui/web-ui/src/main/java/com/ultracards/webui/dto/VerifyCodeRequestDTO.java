package com.ultracards.webui.dto;

/**
 * Data Transfer Object for verification code requests.
 * Matches the server's VerifyCodeRequestDTO structure.
 */
public class VerifyCodeRequestDTO {

    private String email;
    private String code;

    public VerifyCodeRequestDTO() {}

    public VerifyCodeRequestDTO(String email, String code) {
        this.email = email;
        this.code = code;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }
}