package com.ultracards.gateway.dto.auth;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UserTresetaStatsDTO {
    private UUID id;
    private Long userId;
    private Map<String, GameStatsDTO> configStats;
    private List<TresetaMatchupStatsDTO> winsAgainstUser;
    private List<TresetaMatchupStatsDTO> winsWithTeammate;
    private int declarationsMade;
    private int declarationPoints;
}
