package com.ultracards.server.service;

import com.ultracards.gateway.dto.BasicUserDTO;
import com.ultracards.server.entity.UserEntity;
import com.ultracards.server.enums.Role;
import com.ultracards.server.entity.auth.VerificationCode;
import com.ultracards.server.repositories.UserRepository;
import com.ultracards.server.repositories.VerificationCodeRepository;
import com.ultracards.server.service.auth.TokenService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.mail.MessagingException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.io.UnsupportedEncodingException;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
public class LegacyAuthService {

    public static final int CODE_VALIDITY_MINUTES = 10;

    private final UserRepository userRepository;
    private final VerificationCodeRepository codeRepository;
    private final EmailService emailService;
    private final RefreshTokenService refreshTokenService;
    private final TokenService tokenService;

    @Value("${app.jwt.secret.token}")
    private String JWT_SECRET;
    @Value("${app.jwt.token.valid.time.minutes}")
    private Long JWT_TOKEN_VALID_TIME_MINUTES;


    public ResponseEntity<Void> isUserActive(BasicUserDTO user) {
        try {
            var userEntity = userRepository.findById(user.getUserId());

            if (userEntity.isEmpty()) return ResponseEntity.status(HttpStatus.I_AM_A_TEAPOT).build();

            if (refreshTokenService.isValid(user.getToken())) {
                return ResponseEntity.ok().build();
            } else {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
    }

    public void authorizeUser(String email) {
        var user = userRepository.findByEmail(email);

        if (user.isEmpty()) {
            registerUser(email);
        } else {
            loginUser(email);
        }

    }

    public void registerUser(String email) {
        userRepository.findByEmail(email).ifPresent(user -> {
            throw new IllegalStateException("UserEntity with such email already exists!");
        });
        var user = new UserEntity(email, "");
        user.addRole(Role.PLAYER);
        userRepository.save(user);

        generateAndSendCode(user);
    }

    public void loginUser(String email) {
        UserEntity user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("No user found with email: " + email));
        generateAndSendCode(user);
    }

    private void generateAndSendCode(UserEntity user) {
        codeRepository.findAllByUserAndUsedFalse(user).ifPresent(
                list -> list.forEach(code -> {
                    code.setUsed(true);
                    codeRepository.save(code);
                })
        );

        var token = String.format("%06d", ThreadLocalRandom.current().nextInt(0, 1_000_000));
        var timeNow = LocalDateTime.now();
        var verificationCode = new VerificationCode(user, token, timeNow.plusMinutes(CODE_VALIDITY_MINUTES));

        codeRepository.save(verificationCode);

        try {
            emailService.sendVerificationEmail(user, verificationCode);
        } catch (MessagingException | UnsupportedEncodingException e) {
            // Mark the code as used since we couldn't send it
            verificationCode.setUsed(true);
            codeRepository.save(verificationCode);

            throw new IllegalStateException("Failed to send verification email: " + e.getMessage(), e);
        }
    }

    public String verifyCode(String email, String code) {
        var user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("No user found with email: " + email));

        var verCode = codeRepository.findByUserAndCodeAndUsedFalse(user, code)
                .orElseThrow(() -> new IllegalArgumentException("Invalid or expired verification code."));

        if (verCode.isExpired()) {
            throw new IllegalArgumentException("Verification code has expired.");
        }

        verCode.setUsed(true);
        codeRepository.save(verCode);

        return generateJwtToken(user);
    }

    public String generateJwtToken(UserEntity user) {
        var now = new Date();
        var expiry = new Date(System.currentTimeMillis() + JWT_TOKEN_VALID_TIME_MINUTES * 60 * 1000);
        var secretKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(JWT_SECRET));

        return Jwts.builder()
                .header()
                .type("JWT")
                .and()
                .subject(user.getEmail())
                .claim("role", user.getRoles().toString())
                .issuedAt(now)
                .expiration(expiry)
                .signWith(secretKey, Jwts.SIG.HS256) // use the updated signature constant
                .compact();
    }
}
