package com.ultracards.server.entity.auth;


import com.ultracards.server.entity.UserEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDateTime;

@Entity
@Table(name = "verification_codes")
public class VerificationCode {
    @Setter
    @Getter
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Setter
    @Getter
    @Column(nullable = false)
    private String code;

    @Setter
    @Getter
    @Column(nullable = false)
    private Instant expirationTime;

    @Setter
    @Getter
    @ManyToOne(optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    @Setter
    @Getter
    @Column(nullable = false)
    private boolean used = false;

    public VerificationCode() {}

    public VerificationCode(UserEntity user, String code, Instant expirationTime) {
        this.user = user;
        this.code = code;
        this.expirationTime = expirationTime;
    }

    public boolean isExpired() {
        return Instant.now().isAfter(expirationTime);
    }
    public boolean isValid() {
        return !isExpired() && !isUsed();
    }

}
