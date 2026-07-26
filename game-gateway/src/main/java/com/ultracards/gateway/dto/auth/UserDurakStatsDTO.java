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
public class UserDurakStatsDTO {
    private UUID id;
    private Long userId;
    /** Keyed by canonical Durak mode key. */
    private Map<String, GameStatsDTO> configStats;
    private List<DurakMatchupStatsDTO> winsAgainstUser;
    /** How often this user ended up as the durak. */
    private int timesDurak;
    private int draws;
}
