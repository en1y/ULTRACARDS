package com.ultracards.gateway.dto.auth;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class BriskulaMatchupStatsDTO {
    private String gameConfig;
    private Long relatedUserId;
    private String relatedUsername;
    private int played;
    private int wins;
}
