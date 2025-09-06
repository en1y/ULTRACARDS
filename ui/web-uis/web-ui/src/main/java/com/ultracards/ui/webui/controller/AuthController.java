package com.ultracards.ui.webui.controller;

import com.ultracards.gateway.dto.EmailDTO;
import com.ultracards.gateway.dto.auth.UsernameDTO;
import com.ultracards.gateway.dto.auth.VerificationCodeDTO;
import com.ultracards.gateway.service.AuthService;
import com.ultracards.gateway.service.ClientTokenHolder;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/email/send")
    public ResponseEntity<?> sendEmail(
            @Valid @RequestBody EmailDTO email
    ) {
        authService.sendVerificationEmail(email.getEmail());
        return ResponseEntity.ok().body(Map.of("status", "ok"));
    }

    @PostMapping("/email/verify")
    public ResponseEntity<Boolean> verifyEmail(
            @Valid @RequestBody VerificationCodeDTO code,
            HttpServletResponse response
            ) {
        var tokenHolder = new ClientTokenHolder();

        var res = authService.verifyCode(code.getCode(), tokenHolder);

        if (res)
            response.addCookie(createCookie(tokenHolder));

        return ResponseEntity.ok().body(res);
    }

    @PutMapping("/username")
    public ResponseEntity<UsernameDTO> changeUsername(
            @CookieValue("refreshToken") String token,
            @Valid @RequestBody UsernameDTO username,
            HttpServletResponse response
    ) {
        var tokenHolder = new ClientTokenHolder(token);
        var newUsername = authService.updateUsername(
                tokenHolder,
                username.getUsername()
        );

        response.addCookie(createCookie(tokenHolder));
        return ResponseEntity.ok(newUsername);
    }

    private Cookie createCookie(ClientTokenHolder tokenHolder) {
        var cookie = new Cookie("refreshToken", tokenHolder.getToken());
        cookie.setPath("/");
        cookie.setMaxAge(60*60*24);
        cookie.setHttpOnly(true);
        return cookie;
    }
}
