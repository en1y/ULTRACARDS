package com.ultracards.server.service;

import com.ultracards.gateway.dto.EmailDTO;
import com.ultracards.gateway.dto.auth.TokenDTO;
import com.ultracards.gateway.dto.auth.UsernameDTO;
import com.ultracards.gateway.dto.auth.VerificationCodeDTO;
import com.ultracards.server.repositories.UserRepository;
import com.ultracards.server.service.auth.TokenService;
import com.ultracards.server.service.auth.ValidationResult;
import com.ultracards.server.service.auth.VerificationCodeService;
import jakarta.mail.MessagingException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.UnsupportedEncodingException;

import static com.ultracards.server.enums.TokenValidationResultStatus.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    @Value("${app.token.duration-minutes:15}")
    private int tokenDurationMinutes;

    private final VerificationCodeService verificationCodeService;
    private final UserService userService;
    private final UserRepository userRepository;
    private final TokenService tokenService;
    private final EmailService emailService;

    public String updateUsername (UsernameDTO username, String token, HttpServletResponse response) {
        var validatedToken = tokenService.validateToken(new TokenDTO(token));

        if (validatedToken.getStatus().equals(LOGOUT)) return null;
        if (validatedToken.getStatus().equals(ROTATED))
            processRotatedToken(validatedToken, response);

        var userEntity = validatedToken.getUser();
        userEntity.setUsername(username.getUsername());
        userEntity = userRepository.save(userEntity);
        return userEntity.getUsername();
    }

    public String getUsername (String token, HttpServletResponse response) {
        var validatedToken = tokenService.validateToken(new TokenDTO(token));

        if (validatedToken.getStatus().equals(LOGOUT)) return null;
        if (validatedToken.getStatus().equals(ROTATED))
            processRotatedToken(validatedToken, response);

        return validatedToken.getUser().getUsername();
    }

    public void logout(String token, HttpServletRequest request, HttpServletResponse response) {
        if (token != null) {
            tokenService.deleteTokenIfExists(token);
        }

        var cookies = request.getCookies();

        for (var c: cookies) {
            c.setValue("");
            c.setPath(c.getPath());
            c.setMaxAge(0);
            response.addCookie(c);
        }
    }

    public void sendVerificationEmail(@Valid EmailDTO emailDTO, HttpServletResponse response) {

        var user = userService.getUserByEmail(emailDTO);
        var code = verificationCodeService.createVerificationCode(user);

        try {
            emailService.sendVerificationEmail(user, code);
            response.addCookie(new Cookie("token", tokenService.getTokenByUser(user).toString()));
        } catch (MessagingException e) {
            log.error("Failed to send verification email to {}", emailDTO.getEmail(), e);
            throw new IllegalStateException("Failed to send verification email: " + e.getMessage(), e);
        } catch (UnsupportedEncodingException e) {
            log.error("Wrong file encoding was used to send verification email to {}", emailDTO.getEmail(), e);
            throw new IllegalStateException("Wrong file encoding was used to send verification email. " + e.getMessage(), e);
        }
    }

    public Boolean verifyCode(@Valid VerificationCodeDTO verificationCodeDTO, String token, HttpServletResponse response) {
        var validatedToken = tokenService.validateToken(new TokenDTO(token));

        if (validatedToken.getStatus().equals(LOGOUT)) return null;
        if (validatedToken.getStatus().equals(ROTATED))
            processRotatedToken(validatedToken, response);

        var user = validatedToken.getUser();
        var code = verificationCodeService.getVerificationCodeByUser(user);

        if (code == null) return null;

        return code.getCode().equals(verificationCodeDTO.getCode());
    }

    private void processRotatedToken(ValidationResult validationResult, HttpServletResponse response) {
        var cookie = new Cookie("token", validationResult.getToken().toString());

        cookie.setMaxAge(tokenDurationMinutes * 60);
        response.addCookie(cookie);
    }
}
