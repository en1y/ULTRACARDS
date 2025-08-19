package com.ultracards.server.service.auth;

import com.ultracards.server.entity.UserEntity;
import com.ultracards.server.entity.auth.VerificationCode;
import com.ultracards.server.repositories.VerificationCodeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
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
        var timeNow = Instant.now();
        var verificationCode = new VerificationCode(user, token, timeNow.plus(CODE_VALIDITY_MINUTES, ChronoUnit.MINUTES));

        verificationCode = codeRepository.save(verificationCode);
        return verificationCode;
    }

    public VerificationCode getVerificationCodeByUser(UserEntity user) {
        var code = codeRepository.findByUserAndUsedFalse(user).orElse(null);
        if (code != null && code.isValid()) {
            return code;
        }
        else return null;
    }

    public boolean validateVerificationCode(VerificationCode code) {
        if (!code.isValid()) return false;
        code.setUsed(true);
        codeRepository.save(code);
        return true;
    }

}
