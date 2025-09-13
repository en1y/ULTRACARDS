package com.ultracards.server.service;

import com.ultracards.gateway.dto.EmailDTO;
import com.ultracards.server.entity.UserEntity;
import com.ultracards.server.enums.Role;
import com.ultracards.server.repositories.UserRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class UserService {
    @Value("${app.max-length.email:150}")
    private Integer MAX_EMAIL_LENGTH;

    private final UserRepository userRepository;

    public UserEntity createUser(@Valid EmailDTO email) {
        if (email.getEmail().length() > MAX_EMAIL_LENGTH)
            throw new IllegalArgumentException("Email too long");
        var user = new UserEntity(email.getEmail(), "");
        user.addRole(Role.USER);
        user = userRepository.save(user);
        return user;
    }

    public UserEntity getUserByEmail(@Valid EmailDTO email) {
        var user = userRepository.findByEmail(email.getEmail());
        return user.orElseGet(() -> createUser(email));
    }

    public boolean userExistsByEmail(@Valid EmailDTO email) {
        return userRepository.findByEmail(email.getEmail()).isPresent();
    }

    public void updateLastLogInDate(@Valid UserEntity user) {
        user.setLastLoginAt(Instant.now());
        userRepository.save(user);
    }

}
