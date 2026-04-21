package com.ultracards.server.controllers.auth;

import com.ultracards.gateway.dto.EmailDTO;
import com.ultracards.gateway.dto.auth.ProfileDTO;
import com.ultracards.gateway.dto.auth.UsernameDTO;
import com.ultracards.gateway.dto.auth.VerificationCodeDTO;
import com.ultracards.server.entity.UserEntity;
import com.ultracards.server.entity.auth.UserSession;
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

import java.time.Instant;

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

        if (emailDTO != null && !user.getEmail().equals(emailDTO.getEmail()))
            return ResponseEntity.status(HttpStatus.CONFLICT).build();

        authService.sendVerificationEmail(user);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/email/verify")
    public ResponseEntity<Void> verifyCode(
            @AuthenticationPrincipal UserEntity user,
            @RequestAttribute(value = "token", required = false) String token,
            @RequestBody @NotNull @Valid VerificationCodeDTO verificationCodeDTO,
            BindingResult errors,
            HttpServletRequest req
    ) {
        if (errors.hasErrors())
            return ResponseEntity.badRequest().build();

        var userSet = true;

        if (user != null) {
            verificationCodeDTO.setEmail(user.getEmail());
            userSet = false;
        }

        var isVerificationCodeCorrect = authService.verifyCode(verificationCodeDTO);

        if (!isVerificationCodeCorrect)
            return ResponseEntity.badRequest().build();

        user = userService.getUserByEmail(new EmailDTO(verificationCodeDTO.getEmail()));
        if (userSet){
            var session = sessionService.recreateTokenForSession(token);
            var cookie = buildRefreshTokenCookie(session.getTokenString(), 24L * 3600 * tokenDurationDays);
            return ResponseEntity.ok().header("Set-Cookie", cookie.toString()).build();
        }
        token = sessionService.createSession(user, req).getTokenString();
        var cookie = buildRefreshTokenCookie(token, 24L * 3600 * tokenDurationDays);
        return ResponseEntity.ok().header("Set-Cookie", cookie.toString()).build();
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
