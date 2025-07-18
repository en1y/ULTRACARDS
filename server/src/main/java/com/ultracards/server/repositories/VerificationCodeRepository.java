package com.ultracards.server.repositories;

import com.ultracards.server.entity.UserEntity;
import com.ultracards.server.entity.auth.VerificationCode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface VerificationCodeRepository extends JpaRepository<VerificationCode, Long> {
    Optional<VerificationCode> findByUserAndCodeAndUsedFalse(UserEntity user, String code);
    Optional<List<VerificationCode>> findAllByUserAndUsedFalse(UserEntity user);
//    void deleteByExpiredAfter(LocalDateTime cutoff); // TODO implement removal when expired
}
