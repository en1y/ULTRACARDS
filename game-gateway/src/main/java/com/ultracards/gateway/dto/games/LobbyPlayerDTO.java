package com.ultracards.gateway.dto.games;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class LobbyPlayerDTO {
    private Long id;
    private String username;
}
