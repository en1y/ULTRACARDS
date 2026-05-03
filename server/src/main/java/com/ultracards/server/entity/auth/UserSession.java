package com.ultracards.server.entity.auth;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "sessions")
@AllArgsConstructor
@NoArgsConstructor
@Getter @Setter
public class UserSession {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    private Long userId;
    @OneToOne(fetch = FetchType.EAGER, optional = false, cascade = CascadeType.REMOVE)
    @JoinColumn(name = "token_id", nullable = false)
    private TokenEntity token;
    @Column private String deviceId;
    @Column private String clientType;
    @Column private String os;
    @Column(nullable = false) private String ipHash;
    @Column private String country;
    @Column private String region;
    @Column(length = 512) private String userAgent;

    @Column(nullable = false) private Instant firstSeenAt;
    @Column(nullable = false) private Instant lastSeenAt;
    @Column(nullable = false) private Instant lastAuthenticatedAt;

    @Transient private Boolean currentSession;

    public UserSession(
            Long userId,
            TokenEntity token,
            String deviceId,
            String clientType,
            String os,
            String ipHash,
            String country,
            String region,
            String userAgent) {
        this.userId = userId;
        this.token = token;
        this.deviceId = deviceId;
        this.clientType = clientType;
        this.os = os;
        this.ipHash = ipHash;
        this.country = country;
        this.region = region;
        this.userAgent = userAgent;
        this.firstSeenAt = Instant.now();
        this.lastSeenAt = Instant.now();
        this.lastAuthenticatedAt = Instant.now();
        this.currentSession = false;
    }

    public String getTokenString() {
        return token.getToken();
    }
}
