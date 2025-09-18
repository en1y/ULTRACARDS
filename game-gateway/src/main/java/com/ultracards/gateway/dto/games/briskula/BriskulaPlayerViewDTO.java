package com.ultracards.gateway.dto.games.briskula;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BriskulaPlayerViewDTO {
    private Long userId;
    private String username;
    private int points;
    private List<BriskulaCardDTO> hand;
    private int handSize;
    private int capturedCards;
    private boolean currentTurn;
}
