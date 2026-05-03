package com.ultracards.gateway.dto.auth;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Map;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UserGamesStatsDTO {
    private UUID id;
    private Long userId;
    private Map<String, GameStatsDTO> gameStats;
}
