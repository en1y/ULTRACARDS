package com.ultracards.server.service;

import com.ultracards.server.entity.UserEntity;
import com.ultracards.server.entity.auth.VerificationCode;
import com.ultracards.server.repositories.VerificationCodeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
public class VerificationCodeService {
    @Value("${app.code.validity-minutes:10}")
    private int CODE_VALIDITY_MINUTES;

    private final VerificationCodeRepository codeRepository;

    public VerificationCode createVerificationCode(UserEntity user) {
        codeRepository.findAllByUserAndUsedFalse(user).ifPresent(
                list -> list.forEach(code -> {
                    code.setUsed(true);
                    codeRepository.save(code);
                })
        );

        var token = String.format("%06d", ThreadLocalRandom.current().nextInt(0, 1_000_000));
        var timeNow = LocalDateTime.now();
        var verificationCode = new VerificationCode(user, token, timeNow.plusMinutes(CODE_VALIDITY_MINUTES));

        verificationCode = codeRepository.save(verificationCode);
        return verificationCode;
    }

}
