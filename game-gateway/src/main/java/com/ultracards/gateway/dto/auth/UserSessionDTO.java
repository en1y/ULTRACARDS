package com.ultracards.gateway.dto.auth;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserSessionDTO {
    @NotNull private UUID id;
    private String deviceId;
    private String clientType;
    private String os;
    private String ipHash;
    private String country;
    private String region;
    private String userAgent;
    @NotNull private Boolean currentSession;
    Instant firstSeenAt;
    Instant lastSeenAt;
    Instant lastAuthenticatedAt;
}
