package com.ultracards.gateway.dto.games.games.durak;

import com.ultracards.gateway.dto.games.GamePlayerDTO;
import com.ultracards.gateway.dto.games.games.GameCardDTO;
import com.ultracards.gateway.dto.games.games.GameEntityDTO;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
 * Public Durak state. Never contains anyone's hand: {@code playersCardsMap} holds counts only.
 */
@Data
@NoArgsConstructor
public class DurakGameEntityDTO extends GameEntityDTO {
    private String trumpSuit;
    private GameCardDTO trumpIndicator;
    private DurakPhaseDTO phase;
    private Long stateRevision;
    private Integer boutNumber;
    private GamePlayerDTO leadAttacker;
    private GamePlayerDTO defender;
    private GamePlayerDTO actionPlayer;
    private Integer maxAttackCards;
    private List<DurakAttackSlotDTO> attackSlots;
    private List<GamePlayerDTO> eligibleThrowers;
    private List<GamePlayerDTO> doneThrowers;
    private Boolean takeDeclared;
    private Boolean passingEnabled;
    private Boolean jokersEnabled;
    private DurakThrowInPolicyDTO throwInPolicy;
    private List<GamePlayerDTO> finishedPlayers;
    private List<GamePlayerDTO> finishOrder;
    private Integer discardedCardsNum;
    private Instant turnEndTime;
    private Integer turnDurationSeconds;
}
