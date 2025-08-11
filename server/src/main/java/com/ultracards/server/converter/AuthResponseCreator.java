package com.ultracards.server.converter;

import com.ultracards.gateway.dto.AuthResponseDTO;
import com.ultracards.server.entity.UserEntity;

public class AuthResponseCreator {
    public static AuthResponseDTO create(String token, UserEntity user) {
        return new AuthResponseDTO(
                token,
                user.getEmail(),
                user.getUsername(),
                user.getRole().name(),
                user.getId()
        );
    }
}
