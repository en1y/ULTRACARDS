package com.ultracards.server.controllers;

import com.ultracards.server.dto.AuthResponseDTO;
import com.ultracards.server.dto.EmailRequestDTO;
import com.ultracards.server.dto.VerifyCodeRequestDTO;
import com.ultracards.server.entity.UserEntity;
import com.ultracards.server.entity.auth.RefreshTokenEntity;
import com.ultracards.server.repositories.UserRepository;
import com.ultracards.server.service.AuthService;
import com.ultracards.server.service.RefreshTokenService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.Optional;

@RestController
@RequestMapping("/auth")
public class AuthController {
    private final AuthService authService;
    private final RefreshTokenService refreshTokenService;
    private final UserRepository userRepository;

    @Value("${app.jwt.token.valid.time.minutes}")
    private int jwtExpirationMinutes;

    public AuthController(AuthService authService, RefreshTokenService refreshTokenService, UserRepository userRepository) {
        this.authService = authService;
        this.refreshTokenService = refreshTokenService;
        this.userRepository = userRepository;
    }

    @PostMapping("/set-username")
    public ResponseEntity<Void> setUsername(@RequestBody EmailRequestDTO request) {
        try {
            var user = userRepository.findByEmail(request.getEmail()).orElseThrow();
            user.setUsername(request.getUsername());
            userRepository.save(user);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
    }

    @PostMapping("/authorize")
    public ResponseEntity<Void> authorize(@RequestBody EmailRequestDTO request) {
        System.out.println("I am here");
        try {
            authService.authorizeUser(request.getEmail());
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
    }

    @PostMapping("/verify") // After email verification
    public ResponseEntity<AuthResponseDTO> verifyCode(@RequestBody VerifyCodeRequestDTO request, HttpServletResponse response) {
        var jwt = authService.verifyCode(request.getEmail(), request.getCode());
        var user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new IllegalArgumentException("User not found with email: " + request.getEmail()));

        var refreshToken = refreshTokenService.createRefreshToken(user);
        setRefreshTokenCookie(refreshToken.getToken(), response);

        return ResponseEntity.ok(new AuthResponseDTO(jwt, user));
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponseDTO> refreshToken(@CookieValue(name = "refreshToken", required = false) String refreshTokenCookie, HttpServletResponse response) {
        if (refreshTokenCookie == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        var refreshTokenOpt = refreshTokenService.findByToken(refreshTokenCookie);
        if (refreshTokenOpt.isEmpty() || refreshTokenService.isExpired(refreshTokenOpt.get())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        UserEntity user = refreshTokenOpt.get().getUser();
        String jwt = authService.generateJwtToken(user);


         refreshTokenService.deleteRefreshToken(refreshTokenOpt.get());
         RefreshTokenEntity newRefreshToken = refreshTokenService.createRefreshToken(user);
         setRefreshTokenCookie(newRefreshToken.getToken(), response);

        return ResponseEntity.ok(new AuthResponseDTO(jwt, user));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@CookieValue(name = "refreshToken", required = false) String refreshTokenCookie,
                                       HttpServletResponse response) {
        if (refreshTokenCookie != null) {
            refreshTokenService.findByToken(refreshTokenCookie)
                    .ifPresent(refreshTokenService::deleteRefreshToken);
            clearRefreshTokenCookie(response);
        }
        return ResponseEntity.noContent().build();
    }

    private void setRefreshTokenCookie(String token, HttpServletResponse response) {
        Cookie cookie = new Cookie("refreshToken", token);
        cookie.setHttpOnly(true);
        cookie.setSecure(true); // set to true for HTTPS environments
        cookie.setPath("/auth/refresh");
        cookie.setMaxAge((int) Duration.ofDays(30).getSeconds());
        response.addCookie(cookie);
    }

    private void clearRefreshTokenCookie(HttpServletResponse response) {
        Cookie cookie = new Cookie("refreshToken", "");
        cookie.setHttpOnly(true);
        cookie.setSecure(true); // HTTPS recommended
        cookie.setPath("/auth/refresh");
        cookie.setMaxAge(0);
        response.addCookie(cookie);
    }
}
