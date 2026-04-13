package com.ultracards.server.controllers;

import com.ultracards.gateway.dto.EmailDTO;
import com.ultracards.gateway.dto.auth.ProfileDTO;
import com.ultracards.gateway.dto.auth.UsernameDTO;
import com.ultracards.gateway.dto.auth.VerificationCodeDTO;
import com.ultracards.server.entity.UserEntity;
import com.ultracards.server.service.EmailService;
import com.ultracards.server.service.auth.AuthService;
import com.ultracards.server.service.UserService;
import com.ultracards.server.service.auth.SessionService;
import com.ultracards.server.service.auth.TokenService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final TokenService tokenService;
    private final UserService userService;
    private final SessionService sessionService;
    private final EmailService emailService;

    @Value("${app.max-length.username}")
    private Integer MAX_USERNAME_LENGTH;

    @Value("${app.cookie-token.duration-days:15}")
    private int tokenDurationDays;

    @Value("${app.cookie-token.same-site:Lax}")
    private String sameSite;

    @Value("${app.cookie-token.secure:false}")
    private boolean cookieSecure;

    @Value("${app.cookie-token.domain:}")
    private String cookieDomain;

    @PutMapping(
            value = "/username",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    @ResponseBody
    @PreAuthorize("hasRole(T(com.ultracards.server.enums.UserRole).USER.name())")
    public ResponseEntity<?> updateUsername(
            @AuthenticationPrincipal UserEntity user,
            @Valid @RequestBody UsernameDTO username,
            BindingResult errors) {
        if (errors.hasErrors() || username.getUsername().length() > MAX_USERNAME_LENGTH) {
            var errorsString = errors.getAllErrors().stream().map(e -> e.getDefaultMessage()).toList().toString();
            return ResponseEntity.badRequest().body(errorsString);
        }

        authService.updateUsername(user, username);

        if (username.getUsername() == null)
            return redirectToLogout();
        return ResponseEntity.ok(username);
    }

    @GetMapping("/username")
    @PreAuthorize("hasRole(T(com.ultracards.server.enums.UserRole).USER.name())")
    public ResponseEntity<UsernameDTO> getUsername(
            @AuthenticationPrincipal UserEntity user
    ) {
        return ResponseEntity
                .ok(new UsernameDTO(user.getUsername()));
    }

    @PostMapping("/logout")
    public ResponseEntity<String> logout(
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        authService.logout(request, response);
        return ResponseEntity.ok("Logged out");
    }

    @PostMapping("/email/send")
    public ResponseEntity<Void> sendVerificationEmail(
            @AuthenticationPrincipal UserEntity user,
            @RequestBody(required = false) EmailDTO emailDTO,
            BindingResult errors
    ) {
        if (errors.hasErrors()) {
            return ResponseEntity.badRequest().build();
        }
        if (user == null) {
            var userByEmail = userService.userExistsByEmail(emailDTO);
            if (userByEmail)
                user = userService.getUserByEmail(emailDTO);
            else
                user = userService.createUser(emailDTO);
        }

        if (!user.getEmail().equals(emailDTO.getEmail()))
            return ResponseEntity.status(HttpStatus.CONFLICT).build();

        authService.sendVerificationEmail(emailDTO, user);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/email/verify")
    public ResponseEntity<Void> verifyCode(
            @AuthenticationPrincipal UserEntity user,
            @RequestBody @NotNull @Valid VerificationCodeDTO verificationCodeDTO,
            BindingResult errors,
            HttpServletRequest req
    ) {
        if (errors.hasErrors())
            return ResponseEntity.badRequest().build();

        if (user != null) {
            verificationCodeDTO.setEmail(user.getEmail());
        }

        var isVerificationCodeCorrect = authService.verifyCode(verificationCodeDTO);

        if (!isVerificationCodeCorrect)
            return ResponseEntity.badRequest().build();

        user = userService.getUserByEmail(new EmailDTO(verificationCodeDTO.getEmail()));
        var session = sessionService.createSession(user, req);
        var token = session.getTokenString();

        var cookie = buildRefreshTokenCookie(token, 24L * 3600 * tokenDurationDays);
        return ResponseEntity.ok().header("Set-Cookie", cookie.toString()).build();
    }

    @GetMapping("/profile")
    @PreAuthorize("hasRole(T(com.ultracards.server.enums.UserRole).USER.name())")
    public ResponseEntity<ProfileDTO> getProfile(
            @AuthenticationPrincipal UserEntity user
    ) {
        var res = authService.getProfile(user);

        return ResponseEntity.ok(res);
    }

    @PostMapping("/profile")
    @PreAuthorize("hasRole(T(com.ultracards.server.enums.UserRole).USER.name())")
    public ResponseEntity<ProfileDTO> updateProfile(
            @AuthenticationPrincipal UserEntity user,
            @RequestBody @Valid ProfileDTO profileDTO,
            BindingResult errors
    ) {
        if (errors.hasErrors()) {
            return ResponseEntity.badRequest().build();
        }

        if (user == null) {
            return ResponseEntity.badRequest().build();
        }
        var res = authService.updateProfile(profileDTO, user);

        return ResponseEntity.ok(res);
    }

    private <T> ResponseEntity<T> redirectToLogout() {
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, "/api/auth/logout")
                .build();
    }

    private ResponseCookie buildRefreshTokenCookie(String tokenValue, long maxAgeSeconds) {
        var cookie = ResponseCookie.from("refreshToken", tokenValue)
                .path("/")
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite(sameSite)
                .maxAge(maxAgeSeconds);

        if (cookieDomain != null && !cookieDomain.isBlank()) {
            cookie.domain(cookieDomain);
        }

        return cookie.build();
    }
}
