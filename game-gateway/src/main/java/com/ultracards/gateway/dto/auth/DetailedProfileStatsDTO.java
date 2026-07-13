package com.ultracards.gateway.dto.auth;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DetailedProfileStatsDTO {
    private UserGamesStatsDTO userGamesStats;
    private UserBriskulaStatsDTO userBriskulaStats;
    private UserTresetaStatsDTO userTresetaStats;
}
