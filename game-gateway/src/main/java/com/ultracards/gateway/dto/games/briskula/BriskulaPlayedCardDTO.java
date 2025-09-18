package com.ultracards.gateway.dto.games.briskula;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BriskulaPlayedCardDTO {
    private Long userId;
    private String username;
    private BriskulaCardDTO card;
}
