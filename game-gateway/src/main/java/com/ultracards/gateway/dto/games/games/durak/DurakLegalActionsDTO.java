package com.ultracards.gateway.dto.games.games.durak;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * What one specific authenticated player may legally do right now. Advisory only: the server
 * stays authoritative even when a stale client still shows an action as available.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DurakLegalActionsDTO {
    private Long stateRevision;
    private List<DurakActionTypeDTO> allowedActionTypes;
    private List<Integer> defendableSlotIds;
    private List<String> throwableCardCodes;
    private List<String> passableCardCodes;
}
