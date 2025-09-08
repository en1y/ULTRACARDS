package com.ultracards.ui.webui.controller;

import com.ultracards.gateway.dto.EmailDTO;
import com.ultracards.gateway.dto.UserDTO;
import com.ultracards.gateway.dto.auth.ProfileDTO;
import com.ultracards.gateway.dto.auth.UsernameDTO;
import com.ultracards.gateway.dto.auth.VerificationCodeDTO;
import com.ultracards.gateway.service.AuthService;
import com.ultracards.gateway.service.ClientTokenHolder;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.security.auth.login.AccountLockedException;
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
            @Valid @RequestBody EmailDTO email,
            @CookieValue(name = "refreshToken", required = false) String token,
            HttpServletResponse response
    ) {
        if (token == null) {
            authService.sendVerificationEmail(email.getEmail());
        } else {
            var tokenHolder = new ClientTokenHolder(token);
            try {
                authService.sendVerificationEmail(email.getEmail(), tokenHolder);
            } catch (AccountLockedException e) {
                // In case of 409
                return ResponseEntity
                        .status(HttpStatus.CONFLICT)
                        .body(Map.of(
                                "error", "ACCOUNT_LOCKED",
                                "message", e.getMessage()
                        ));
            }
            response.addCookie(createCookie(tokenHolder));
        }
        return ResponseEntity.ok().body(Map.of("status", "ok"));
    }

    @PostMapping("/email/verify")
    public ResponseEntity<Map<String, Object>> verifyEmail(
            @CookieValue(name = "refreshToken", required = false) String token,
            @Valid @RequestBody VerificationCodeDTO code,
            HttpServletResponse response
    ) {
        var tokenHolder = token == null ?
            new ClientTokenHolder() : new ClientTokenHolder(token);


        var success = authService.verifyCode(code, tokenHolder);

        var needsUsername = true;
        if (success) {
            response.addCookie(createCookie(tokenHolder));
            var usernameDTO = authService.getUsername(tokenHolder);
            var username = usernameDTO != null ? usernameDTO.getUsername() : null;
            needsUsername = (username == null || username.isBlank());
        }

        return ResponseEntity.ok().body(Map.of(
                "success", success,
                "needsUsername", needsUsername
        ));
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
