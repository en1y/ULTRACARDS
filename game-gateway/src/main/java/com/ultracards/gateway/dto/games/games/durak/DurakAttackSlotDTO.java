package com.ultracards.gateway.dto.games.games.durak;

import com.ultracards.gateway.dto.games.GamePlayerDTO;
import com.ultracards.gateway.dto.games.games.GameCardDTO;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DurakAttackSlotDTO {
    private Integer slotId;
    private GamePlayerDTO attacker;
    private GameCardDTO attackCard;
    private GamePlayerDTO defender;
    private GameCardDTO defenseCard;
}
