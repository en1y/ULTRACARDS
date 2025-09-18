package com.ultracards.gateway.dto.games.briskula;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BriskulaGameStateDTO {
    private UUID gameId;
    private String trumpSuit;
    private BriskulaCardDTO trumpCard;
    private int deckRemaining;
    private List<BriskulaPlayerViewDTO> players;
    private List<BriskulaPlayedCardDTO> trick;
    private Long currentTurnUserId;
    private String currentTurnUsername;
    private boolean finished;
    private List<Long> winners;
    private boolean teamsEnabled;
}
