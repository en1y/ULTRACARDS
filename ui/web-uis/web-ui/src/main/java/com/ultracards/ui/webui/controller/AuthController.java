package com.ultracards.ui.webui.controller;

import com.ultracards.gateway.dto.EmailDTO;
import com.ultracards.gateway.dto.auth.UsernameDTO;
import com.ultracards.gateway.dto.auth.VerificationCodeDTO;
import com.ultracards.gateway.service.AuthenticationService;
import com.ultracards.gateway.service.ClientTokenHolder;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.security.auth.login.AccountLockedException;
import java.time.Duration;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    @Value("${app.cookie-token.duration-days:15}")
    private int cookieTokenDurationDays;
    private final AuthenticationService authService;


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
            response.addHeader(HttpHeaders.SET_COOKIE, createCookie(tokenHolder).toString());
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
            response.addHeader(HttpHeaders.SET_COOKIE, createCookie(tokenHolder).toString());
            var usernameDTO = authService.getUsername(tokenHolder);
            var username = usernameDTO != null ? usernameDTO.getUsername() : null;
            needsUsername = (username == null || username.isBlank());
        }
        // TODO: add a clause if the success is false

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

        response.addHeader(HttpHeaders.SET_COOKIE, createCookie(tokenHolder).toString());
        return ResponseEntity.ok(newUsername);
    }

    @GetMapping("/logout")
    public void logout(
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        nukeAllCookies(request, response);
    }

    private ResponseCookie createCookie(ClientTokenHolder tokenHolder) {
        return ResponseCookie.from("refreshToken", tokenHolder.getToken())
                .httpOnly(true)
                .path("/")
                .maxAge(Duration.ofDays(cookieTokenDurationDays))
                .sameSite("Lax")
                .build();
    }

    static void nukeAllCookies(HttpServletRequest request, HttpServletResponse response) {
        var cookies = request.getCookies();
        if (cookies != null) {
            for (var in : cookies) {
                // Best effort: match name + path (+ domain if present), set Max-Age=0
                var out = new Cookie(in.getName(), "");
                out.setMaxAge(0);
                out.setPath(in.getPath() != null ? in.getPath() : "/");
                if (in.getDomain() != null) out.setDomain(in.getDomain());
                // Preserve flags so deletion works over the same scheme
                out.setHttpOnly(in.isHttpOnly());
                out.setSecure(in.getSecure() || request.isSecure());
                response.addCookie(out);

                // Also try deleting at root path in case original path was different
                if (in.getPath() != null && !"/".equals(in.getPath())) {
                    var root = new Cookie(in.getName(), "");
                    root.setMaxAge(0);
                    root.setPath("/");
                    if (in.getDomain() != null) root.setDomain(in.getDomain());
                    root.setHttpOnly(in.isHttpOnly());
                    root.setSecure(in.getSecure() || request.isSecure());
                    response.addCookie(root);
                }
            }
        }
    }
}
