package com.ultracards.gateway.dto.games.games.durak;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.ultracards.gateway.dto.games.games.GameCardDTO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One Durak action. The server never trusts a client-supplied user or game id, so this DTO has
 * none; unknown fields are rejected so a contract mismatch cannot hide.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = false)
public class DurakActionRequestDTO {
    @NotNull private DurakActionTypeDTO type;
    /** Required for ATTACK, DEFEND, THROW_IN and PASS; must be absent otherwise. */
    @Valid private GameCardDTO card;
    /** Required for DEFEND only. */
    private Integer targetSlotId;
    @NotNull @PositiveOrZero private Long expectedRevision;
}
