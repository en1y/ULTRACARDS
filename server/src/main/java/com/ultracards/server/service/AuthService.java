package com.ultracards.server.service;

import com.ultracards.server.entity.Role;
import com.ultracards.server.entity.UserEntity;
import com.ultracards.server.entity.auth.VerificationCode;
import com.ultracards.server.repositories.UserRepository;
import com.ultracards.server.repositories.VerificationCodeRepository;
import jakarta.mail.MessagingException;
import org.springframework.beans.factory.annotation.Autowired;
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

    public AuthService(EmailService emailService, VerificationCodeRepository codeRepository, UserRepository userRepository) {
        this.emailService = emailService;
        this.codeRepository = codeRepository;
        this.userRepository = userRepository;
    }

    public static final int CODE_VALIDITY_MINUTES = 10;

    public void registerUser(String email, String username) {
        userRepository.findByEmail(email).ifPresent(user -> {
            throw new IllegalStateException("User with such email already exists!");
        });
        var user = new UserEntity(email, username, Role.PLAYER);
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
            throw new RuntimeException(e);
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

        return "";
//        return generateJwtToken(user); // TODO: implement JWT handling
    }
}
