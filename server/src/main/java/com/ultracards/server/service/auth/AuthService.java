package com.ultracards.server.service.auth;

import com.ultracards.gateway.dto.auth.GameStatsDTO;
import com.ultracards.gateway.dto.auth.ProfileDTO;
import com.ultracards.gateway.dto.auth.UserGamesStatsDTO;
import com.ultracards.gateway.dto.auth.UsernameDTO;
import com.ultracards.gateway.dto.auth.VerificationCodeDTO;
import com.ultracards.server.entity.UserEntity;
import com.ultracards.server.enums.games.GameType;
import com.ultracards.server.repositories.UserRepository;
import com.ultracards.server.service.EmailService;
import com.ultracards.server.service.users.UserService;
import com.ultracards.server.service.games.UserGamesStatsService;
import jakarta.mail.MessagingException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

import java.io.UnsupportedEncodingException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final VerificationCodeService verificationCodeService;
    private final UserService userService;
    private final UserRepository userRepository;
    private final EmailService emailService;
    private final SessionService sessionService;

    @Value("${app.cookie-token.same-site:Lax}")
    private String sameSite;
    @Value("${app.cookie-token.secure:false}")
    private boolean cookieSecure;
    @Value("${app.cookie-token.domain:}")
    private String cookieDomain;
    @Value("${app.token.update-privilege-duration-minutes:4}")
    private long updateDuration;

    public Boolean updateUsername(UserEntity user, @Valid UsernameDTO username, String token) {
        var session = sessionService.getSession(token);
        var authenticatedAt = session.getLastAuthenticatedAt();
        if (Instant.now().isAfter(authenticatedAt.plusSeconds(updateDuration * 60)))
            return false;
        user.setUsername(username.getUsername());
        userRepository.save(user);
        return true;
    }

    public String getUsername(UserEntity user) {
        return user.getUsername();
    }

    public void logout(HttpServletRequest request, HttpServletResponse response, String token) {
        if (token != null){
            sessionService.logout(
                    sessionService.getSession(token)
            );
        }

        var cookies = request.getCookies();
        if (cookies == null) {
            expireRefreshTokenCookie(response, "/");
            return;
        }

        for (var cookie : cookies) {
            if ("refreshToken".equals(cookie.getName())) {
                expireRefreshTokenCookie(response, "/");
                continue;
            }

            var expiredCookie = ResponseCookie.from(cookie.getName(), "")
                    .path("/")
                    .maxAge(0)
                    .build();

            response.addHeader("Set-Cookie", expiredCookie.toString());
        }
    }

    public void sendVerificationEmail(UserEntity user) {
        if (!verificationCodeService.hasACodeBeenSentRecently(user)) {
            var code = verificationCodeService.createVerificationCode(user);

            try {
                emailService.sendVerificationEmail(user, code);
            } catch (MessagingException e) {
                log.error("Failed to send verification email to {}", user.getEmail(), e);
                throw new IllegalStateException("Failed to send verification email: " + e.getMessage(), e);
            } catch (UnsupportedEncodingException e) {
                log.error("Wrong file encoding was used to send verification email to {}", user.getEmail(), e);
                throw new IllegalStateException("Wrong file encoding was used to send verification email. " + e.getMessage(), e);
            }
        }
    }

    public Boolean verifyCode(@Valid VerificationCodeDTO verificationCodeDTO) {

        var code = verificationCodeService.getVerificationCodeByEmail(verificationCodeDTO.getEmail());

        if (code == null || !code.isValid()) return null;

        var res = code.getCode().equals(verificationCodeDTO.getCode());

        if (res) {
            verificationCodeService.setVerificationCodeUsed(code);
            userService.updateLastLogInDate(code.getUser());
        }

        return res;
    }

    private void expireRefreshTokenCookie(HttpServletResponse response, String path) {
        var expiredRefreshToken = ResponseCookie.from("refreshToken", "")
                .path(path)
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite(sameSite)
                .maxAge(0);

        if (cookieDomain != null && !cookieDomain.isBlank()) {
            expiredRefreshToken.domain(cookieDomain);
        }

        response.addHeader("Set-Cookie", expiredRefreshToken.build().toString());
    }
}
