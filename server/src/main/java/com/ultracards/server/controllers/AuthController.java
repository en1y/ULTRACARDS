package com.ultracards.server.controllers;

import com.ultracards.gateway.dto.auth.UsernameDTO;
import com.ultracards.server.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Value("${app.max-length.username}")
    private Integer MAX_USERNAME_LENGTH;



    @PutMapping("/username")
    public ResponseEntity<UsernameDTO> updateUsername(@RequestBody UsernameDTO username, @CookieValue(name = "token") String token) {
        if (username.getUsername().length() <= MAX_USERNAME_LENGTH) {
            var res = new UsernameDTO(authService.updateUsername(username, token));
            return ResponseEntity.ok(res);
        } else {
            return ResponseEntity.badRequest().build();
        }
    }
}
