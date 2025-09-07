package com.ultracards.server.controllers;

import com.ultracards.gateway.dto.EmailDTO;
import com.ultracards.gateway.dto.auth.ProfileDTO;
import com.ultracards.gateway.dto.auth.UsernameDTO;
import com.ultracards.gateway.dto.auth.VerificationCodeDTO;
import com.ultracards.server.entity.auth.TokenEntity;
import com.ultracards.server.service.AuthService;
import com.ultracards.server.service.UserService;
import com.ultracards.server.service.auth.TokenService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import java.nio.file.AccessDeniedException;

@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final TokenService tokenService;
    private final UserService userService;

    @Value("${app.cookie-token.duration-days:15}")
    private int tokenDurationDays;

    @Value("${app.max-length.username}")
    private Integer MAX_USERNAME_LENGTH;

    @PutMapping(
            value = "/username",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    @ResponseBody
    public ResponseEntity<UsernameDTO> updateUsername(
            @NotBlank @CookieValue(name = "refreshToken") String token,
            @Valid @RequestBody UsernameDTO username,
            BindingResult errors) {

        var tokenEntity = tokenService.getToken(token);

        if (errors.hasErrors() || username.getUsername().length() > MAX_USERNAME_LENGTH) {
            return ResponseEntity.badRequest().build();
        }

        username = new UsernameDTO(authService.updateUsername(username, tokenEntity));

        if (username.getUsername() == null)
            return redirectToLogout();
        return ResponseEntity.ok(username);
    }

    @GetMapping("/username")
    public ResponseEntity<UsernameDTO> getUsername(
            @NotBlank @CookieValue(name = "refreshToken") String token
            ) {

        var tokenEntity = tokenService.getToken(token);

        var username = authService.getUsername(tokenEntity);

        if (username == null)
            return redirectToLogout();

        return ResponseEntity
                .ok(new UsernameDTO(username));

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
            @CookieValue(name = "refreshToken", required = false) String token,
            @Valid @RequestBody EmailDTO emailDTO,
            BindingResult errors
    ) {
        if (errors.hasErrors()) {
            return ResponseEntity.badRequest().build();
        }

        try {
            var tokenEntity = getNullifiableToken(token, emailDTO);

            authService.sendVerificationEmail(emailDTO, tokenEntity);
            return ResponseEntity.ok().build();

        } catch (AccessDeniedException e) {
            return redirectToLogout();
        }
    }

    @PostMapping("/email/verify")
    public ResponseEntity<Void> verifyCode(
            @RequestBody @NotNull @Valid VerificationCodeDTO verificationCodeDTO,
            BindingResult errors
    ) {
        if (errors.hasErrors())
            return ResponseEntity.badRequest().build();

        var isVerificationCodeCorrect = authService.verifyCode(verificationCodeDTO);

        if (!isVerificationCodeCorrect)
            return ResponseEntity.badRequest().build();

        var token =
                tokenService.getTokenByUser(
                        userService.getUserByEmail(
                                new EmailDTO(verificationCodeDTO.getEmail())));
        var cookie = ResponseCookie.from("refreshToken", token.getToken()).build();
        return ResponseEntity.ok().header("Set-Cookie", cookie.toString()).build();
    }

    @GetMapping("/profile")
    public ResponseEntity<ProfileDTO> getProfile(
            @CookieValue(name = "refreshToken", required = false) String token
    ) {

        var tokenEntity = tokenService.getToken(token);

        var res = authService.getProfile(tokenEntity);

        return ResponseEntity.ok(res);
    }

    @PostMapping("/profile")
    public ResponseEntity<ProfileDTO> updateProfile(
            @CookieValue(name = "refreshToken", required = false) String token,
            @RequestBody @Valid ProfileDTO profileDTO,
            BindingResult errors
    ) {
        var tokenEntity = tokenService.getToken(token);
        if (errors.hasErrors()) {
            return ResponseEntity.badRequest().build();
        }

        var res = authService.updateProfile(profileDTO, tokenEntity);

        return ResponseEntity.ok(res);
    }

    private TokenEntity getNullifiableToken(String token, EmailDTO emailDTO) throws AccessDeniedException {
        if (token == null) {
            var user = userService.getUserByEmail(emailDTO);
            return tokenService.getTokenByUser(user);
        } else {
            return tokenService.getToken(token);
        }
    }

    private <T> ResponseEntity<T> redirectToLogout() {
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, "/api/auth/logout")
                .build();
    }
}
