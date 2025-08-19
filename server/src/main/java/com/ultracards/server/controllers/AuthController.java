package com.ultracards.server.controllers;

import com.ultracards.gateway.dto.EmailDTO;
import com.ultracards.gateway.dto.auth.UsernameDTO;
import com.ultracards.gateway.dto.auth.VerificationCodeDTO;
import com.ultracards.server.service.AuthService;
import com.ultracards.server.service.auth.TokenService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final TokenService tokenService;

    @Value("${app.max-length.username}")
    private Integer MAX_USERNAME_LENGTH;

    @PutMapping("/username")
    public ResponseEntity<UsernameDTO> updateUsername(
            @Valid @RequestBody UsernameDTO username,
            @NotBlank @CookieValue(name = "token") String token,
            HttpServletResponse response) {

        if (username.getUsername().length() <= MAX_USERNAME_LENGTH) {
            var newUsername = authService.updateUsername(username, token, response);

            if (newUsername == null)
                return redirectToLogout();

            return ResponseEntity.ok(new UsernameDTO(newUsername));
        } else {
            return ResponseEntity.badRequest().build();
        }

    }

    @GetMapping("/username")
    public ResponseEntity<UsernameDTO> getUsername(
            @NotBlank @CookieValue(name = "token") String token,
            HttpServletResponse response) {
        var username = authService.getUsername(token, response);

        if (username == null)
            return redirectToLogout();

        return ResponseEntity.ok(new UsernameDTO(username));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @CookieValue(name = "refreshToken", required = false) String token,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        authService.logout(token, request, response);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/email/send")
    public ResponseEntity<Void> sendVerificationEmail(
            @Valid @RequestBody EmailDTO emailDTO,
            HttpServletResponse response
    ) {
        authService.sendVerificationEmail(emailDTO, response);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/email/verify")
    public ResponseEntity<Void> verifyCode(
            @CookieValue(name = "refreshToken", required = false) String token,
            @RequestBody @NotNull @Valid VerificationCodeDTO verificationCodeDTO,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        var res = authService.verifyCode(verificationCodeDTO, token, response);

        if (res == null)
            return redirectToLogout();

        if (res) return ResponseEntity.ok().build();
        else return ResponseEntity.badRequest().build();
    }

    private <T> ResponseEntity<T> redirectToLogout() {
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, "/api/auth/logout")
                .build();
    }
}
