package com.ultracards.server.dto;

import com.ultracards.server.entity.Role;
import com.ultracards.server.entity.UserEntity;

public class UserInfoDTO {
    private String email;
    private String username;
    private Role role;

    public UserInfoDTO() {
    }

    public UserInfoDTO(UserEntity user) {
        this.email = user.getEmail();
        this.username = user.getUsername();
        this.role = user.getRole();
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
