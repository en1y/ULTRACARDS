package com.ultracards.server.service;

import com.ultracards.gateway.dto.auth.UsernameDTO;
import com.ultracards.server.repositories.UserRepository;
import com.ultracards.server.service.auth.TokenService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@AllArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final TokenService tokenService;


    public String updateUsername (UsernameDTO username, String token) {
        var validatedToken = tokenService.validateToken(token);
        var userEntity = validatedToken.getUser();
        userEntity.setUsername(username.getUsername());
        userEntity = userRepository.save(userEntity);
        return userEntity.getUsername();
    }
}
