package com.ultracards.gateway.dto.games.games.durak;

import com.fasterxml.jackson.annotation.JsonTypeName;
import com.ultracards.gateway.dto.games.GameConfigDTO;
import com.ultracards.gateway.dto.games.GamePlayerDTO;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Durak lobby configuration. The toggles are deliberately required: defaults belong in the UI
 * request builder, not in ambiguous server deserialization.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonTypeName("Durak")
public class DurakGameConfigDTO implements GameConfigDTO {
    @NotNull @Min(2) @Max(6) private Integer numberOfPlayers;
    @NotNull private Integer cardsInHandNum;
    /** Physical pack choice: 24, 36 or 54. A 54-card pack is 52 suited cards plus two Jokers. */
    @NotNull private Integer deckSize;
    /** Only valid with {@code deckSize == 54}. */
    @NotNull private Boolean jokersEnabled;
    @NotNull private DurakThrowInPolicyDTO throwInPolicy;
    @NotNull private Boolean passingEnabled;
    private List<GamePlayerDTO> orderedUsers;
    /** Derived, read-only; ignored on input. */
    private String modeKey;

    public DurakGameConfigDTO(Integer numberOfPlayers, Integer deckSize, Boolean jokersEnabled,
                              DurakThrowInPolicyDTO throwInPolicy, Boolean passingEnabled,
                              List<GamePlayerDTO> orderedUsers) {
        this(numberOfPlayers, 6, deckSize, jokersEnabled, throwInPolicy, passingEnabled, orderedUsers, null);
    }
}
