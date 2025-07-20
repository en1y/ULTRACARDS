package com.ultracards.server.service;

import com.ultracards.server.entity.Role;
import com.ultracards.server.entity.UserEntity;
import com.ultracards.server.entity.auth.VerificationCode;
import com.ultracards.server.repositories.UserRepository;
import com.ultracards.server.repositories.VerificationCodeRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.mail.MessagingException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.UnsupportedEncodingException;
import java.time.LocalDateTime;
import java.time.temporal.TemporalUnit;
import java.util.Date;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final VerificationCodeRepository codeRepository;
    private final EmailService emailService;

    @Value("${app.jwt.secret.token}")
    private String JWT_SECRET;
    @Value("${app.jwt.token.valid.time.minutes}")
    private Long JWT_TOKEN_VALID_TIME_MINUTES;

    public AuthService(UserRepository userRepository,
                       VerificationCodeRepository codeRepository,
                       EmailService emailService) {
        this.userRepository = userRepository;
        this.codeRepository = codeRepository;
        this.emailService = emailService;
    }

    public static final int CODE_VALIDITY_MINUTES = 10;


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
            throw new IllegalStateException("User with such email already exists!");
        });
        var user = new UserEntity(email, "", Role.PLAYER);
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
                .claim("role", user.getRole().toString())
                .issuedAt(now)
                .expiration(expiry)
                .signWith(secretKey, Jwts.SIG.HS256) // use the updated signature constant
                .compact();
    }
}
